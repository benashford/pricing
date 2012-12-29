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


# How it was built

TBC