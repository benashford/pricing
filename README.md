[![Build Status](https://secure.travis-ci.org/benashford/pricing.png)](http://travis-ci.org/benashford/pricing)

An experiment in Domain Specific Languages in Clojure.

Specifically one which can be used to model the licencing (and assorted add-ons) of a hypothetical evil enterprise software suite.

# How to use...

See `core.clj` for an example of usage.  Below is a description of the thinking behind each feature that the example makes use of.

The namespace `pricing.engine` contains the full DSL.

To define a pricing model, the `defmodel` macro is provided, this takes a name and a body.  This macro expands to a function with the name provided which can be called directly.  The function accepts a map containing key value pairs containing the data required to calculate a price; the function returns another map containing the broken down pricing information.  For example, this is what happens if you define an empty model:

```
user=> (use 'pricing.engine)
nil
user=> (defmodel empty-model)
#'user/empty-model
user=> (empty-model {})
{:status :quote}
```

As you can see there's always at least one key in the result map: `status`.  This indicates whether the model was able to produce a valid price or not, and if not, why not:

```
user=> (defmodel wont-quote (attr :total (no-quote "We don't do quotes")))
#'user/wont-quote
user=> (wont-quote {})
{:status :noquote, :reason "We don't do quotes"}
```

## Attributes
A valid quote will consist of one or many attributes, at the very least there'll be one representing the total price; to define one:

```clojure
(defmodel single-attribute
	(attr :total 100))
```

Which can be used as follows:

```
user=> (single-attribute {})
{:total 100, :status :quote}
```

## Referencing input data
Since this pricing engine is intended to be dynamic, it needs to be able to reference data that's fed into it.  This can be done by referencing the var `in`.  For example:

```clojure
(defmodel single-attribute-2
	(attr :total (* 10 (in :quantity))))
```
```
user=> (single-attribute-2 {:quantity 2})
{:total 20, :status :quote}
```

## Referencing other attributes
This can be done by simply using the keyword for that attribute:

```clojure
(defmodel simple-model
	(attr :unit-price 10)
	(attr :total (* :unit-price (in :quantity))))
```
```
user=> (simple-model {:quantity 2})
{:total 20, :unit-price 10, :status :quote}
```

## Lookup tables
Of course arbitrary constants only go so far, odds are any non-trivial system would require lookup tables.  Here, I present two:

### `table`
A `table` is a simple table looked-up by a key:

```clojure
(defmodel simple-table
	(attr :total (lookup :unit-price (in :type)))
	(table :unit-price
		["a" 1]
		["b" 10]
		["c" 100]))
```

Results:

```
user=> (simple-table {:type "b"})
{:total 10, :status :quote}
user=> (simple-table {:type "c"})
{:total 100, :status :quote}
```

Attempting to use a non-existant key results in the whole thing being rejected:

```
user=> (simple-table {:type "d"})
{:status :noquote, :reason "No such key: d in table: :unit-price"}
```

### `range-table`
A `range-table` is intended for looking up continuous values.  It's defined by specifying the start of each range and the value assigned, the range continues until the start of the next one.  

#### Infinite `range-table`s

The top range, is infinite:

```clojure
(defmodel simple-range
	(attr :quantity (in :quantity))
	(attr :total (* (lookup :unit-price :quantity) :quantity))
	(range-table :unit-price
		[0   10.0]
		[10  9.5]
		[100 9.0]))
```

Results:

```
user=> (simple-range {:quantity 4})
{:total 40.0M, :quantity 4, :status :quote}
user=> (simple-range {:quantity 40})
{:total 380.0M, :quantity 40, :status :quote}
user=> (simple-range {:quantity 400})
{:total 3600.0M, :quantity 400, :status :quote}
```

#### Limited `range-table`s

An upper limit can be applied to a `range-table` by using the keyword `:stop`, for example:

```clojure
(defmodel simple-range-limited
	(attr :quantity (in :quantity))
	(attr :total (* (lookup :unit-price :quantity) :quantity))
	(range-table :unit-price
		[0   10.0]
		[10  9.5]
		[100 9.0]
		[200 :stop]))
```

Results:

```
user=> (simple-range-limited {:quantity 4})
{:total 40.0M, :quantity 4, :status :quote}
user=> (simple-range-limited {:quantity 40})
{:total 380.0M, :quantity 40, :status :quote}
user=> (simple-range-limited {:quantity 400})
{:status :noquote, :reason "No such key: 400 in table: :unit-price"}
```

## Nesting attributes
What if your pricing model includes multiple groups of attributes.  For example, our hypothetical evil enterprise software suite pricing model may have a group called "licensing" and one called "support", both of which may have similiar attributes (e.g. unit cost).  (A full example is [here](src/pricing/core.clj).)  In these cases you can group attributes as items:

```clojure
(defmodel item-example
    (item :the-item
        (attr :quantity (in :quantity))
        (attr :unit-price 4)
        (attr :total (* :quantity :unit-price))))
```

Result:

```
user=> (item-example {:quantity 12})
{:the-item {:total 48, :unit-price 4, :quantity 12}, :status :quote}
```

### Referencing nested attributes

Nested attributes can be referenced with a simple dotted notation:

```clojure
(defmodel item-example-2
	(item :item-1
		(attr :total 100.0))
	(item :item-2
		(attr :total 203.12))
	(attr :grand-total (+ :item-1.total :item-2.total)))
```

Result:

```
user=> (item-example-2 {})
{:grand-total 303.12M, :item-2 {:total 203.12M}, :item-1 {:total 100.0M}, :status :quote}
```

## Aggregating nested attributes
Now imagine you have a several dozen nested attributes, e.g. pricing components, it would be quite tedious (and difficult to maintain) to add a `total` attribute that added them all together.  Instead you can aggregate them:

```clojure
(defmodel aggregation-example
	(item :components
		(item :a
			(attr :total 1))
		(item :b
			(attr :total 2))
		(item :c
			(attr :total 3))
		(aggregation :total +)))
```

Result:

```
user=> (aggregation-example {})
{:components {:total-apportionment-factor 1, :total 6, :c {:total-before-apportionment 3, :total 3}, :b {:total-before-apportionment 2, :total 2}, :a {:total-before-apportionment 1, :total 1}}, :status :quote}
```

It takes two parameters: 1) the name of the attribute to aggregate; and 2) the function to use.  The result is an attribute at the parent level with the same name.

## Apportionment

Use case: your hypothetical evil enterprise software suite has a minimum price, so when a customer opts for the most basic option there's still a minimum price which is apportioned onto the bill.

```clojure
(defmodel apportionment-example
	(attr :number-of-employees (in :number-of-employees))
	(item :components
		(item :licence
			(attr :total (* 10 :number-of-employees)))
		(item :training
			(attr :total (* 2500 :number-of-employees)))
		(item :support
			(attr :total (* 100 :number-of-employees)))
		(aggregation :total + minimum-of 5000.0)))
```

Results:

```
user=> (apportionment-example {:number-of-employees 1})
{:components {:total-apportionment-factor 1.91570881226M, :total 5000.0M, :support {:total-before-apportionment 100, :total 191.570881226M}, :training {:total-before-apportionment 2500, :total 4789.27203065M}, :licence {:total-before-apportionment 10, :total 19.1570881226M}}, :number-of-employees 1, :status :quote}
user=> (apportionment-example {:number-of-employees 2})
{:components {:total-apportionment-factor 1, :total 5220, :support {:total-before-apportionment 200, :total 200}, :training {:total-before-apportionment 5000, :total 5000}, :licence {:total-before-apportionment 20, :total 20}}, :number-of-employees 2, :status :quote}
user=> (apportionment-example {:number-of-employees 3})
{:components {:total-apportionment-factor 1, :total 7830, :support {:total-before-apportionment 300, :total 300}, :training {:total-before-apportionment 7500, :total 7500}, :licence {:total-before-apportionment 30, :total 30}}, :number-of-employees 3, :status :quote}
```

Or in a more readable way:

```
user=> (->> [1 2 3] (map #(apportionment-example {:number-of-employees %})) (map #(get-in % [:components :total])))
(5000.0M 5220 7830)
```

# How it was built

This DSL has been implemented with a healthy dose of macros and dynamic binding.

Macros are used to expand each principal element (e.g. `defmodel`, `item`, `attr`, etc.) into a function; in the case of `defmodel` this function is bound to the namespace in which `defmodel` is used; the other functions are kept within a list of functions that are called in sequence at runtime.  The lookup tables are expanded into `cond` statements.

Dynamic binding is used both during the macro-expansion stage, to allow nesting of `item`s amonst other things, but also at runtime so that the `in` and `out` (usually not directly referenced) vars are as would be expected at runtime based on their order when declared before expansion.

The full engine of the DSL is approximately 180 lines, and can be found in [engine.clj](src/pricing/engine.clj).

# Still to-do

Much, see [`TODO`](TODO).  Since this was my first attempt at a Clojure DSL there's also a list of inefficiencies that I also intend to remove (e.g. where I've replicated stuff that's in the core library - e.g. for walking through the code during macro-expansion).