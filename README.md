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

```
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

```
(defmodel single-attribute-2
	(attr :total (* 10 (in :quantity))))
```
```
user=> (single-attribute-2 {:quantity 2})
{:total 20, :status :quote}
```

## Referencing other attributes
This can be done by simply using the keyword for that attribute:

```
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

```
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

```
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

```
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

```
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

```
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


# How it was built

TBC