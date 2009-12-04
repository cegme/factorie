package cc.factorie
import scala.util.Random
import scala.reflect.Manifest
import scalala.Scalala._
import scalala.tensor.Vector
import scalala.tensor.dense.DenseVector
import scalala.tensor.sparse.{SparseVector, SparseBinaryVector, SingletonBinaryVector}
import cc.factorie.util.{Log, ConsoleLogging, LinkedHashSet}
import cc.factorie.util.Implicits._

// Categorical variables are Discrete variables in which the integers 0...N have each been mapped to some other objects of type ValueType.

// Mapping of changes in mid-November 2009:
// IndexedVariable => CategoricalValues
// SingleIndexed => CategoricalValue
// SingleIndexedVariable => CategoricalVariable
// TypedSingleIndexedObservation => TypedCategoricalValue
// TypedSingleIndexedVariable => TypedCategoricalVariable
// TrueIndexedValue => TrueCategoricalValue
// TypedTrueIndexedValue => TypedTrueCategoricalValue
// PrimitiveVariable => RefVariable
// ItemizedVariable => ItemizedValue

/** For use with variables whose values are mapped to densely-packed integers from 0 and higher, using a CategoricalDomain.
    It can apply to a single int value (as in EnumVariable or CategoricalValue) or a collection of indices (as in BinaryVectorVariable) */
// Semantically "Values" are not really "Variables", but we must inherit from cc.factorie.Variable in order to handle Domain properly
@DomainInSubclasses
trait CategoricalValues extends Variable with DiscreteValues with TypedValues {
  type VariableType <: CategoricalValues
  type DomainType <: CategoricalDomain[VariableType]
  class DomainClass extends CategoricalDomain[VariableType]
  override def domainSize = domain.allocSize
}

/** A DiscreteValue whose integers 0...N are associated with an categorical objects of type ValueType.
    If you are looking for a concrete implementation, consider EnumObservation or EnumVariable or CoordinatedEnumVariable. */
@DomainInSubclasses
abstract trait CategoricalValue extends CategoricalValues with TypedValue with DiscreteValue {
  this: Variable =>
  type VariableType <: CategoricalValue
  //def value: ValueType = domain.get(index) // TODO I wanted to define this here, but Scala cannot resolve the right type.
  //override def toString = printName + "(" + (if (value == this) "this" else value.toString + "=") + index + ")"
} 

/** A DiscreteVariable whose integers 0...N are associated with an object of type ValueType. */
@DomainInSubclasses
abstract trait CategoricalVariable extends Variable with CategoricalValue with DiscreteVariable {
  type VariableType <: CategoricalVariable
  // final def set(newValue: ValueType)(implicit d: DiffList) = setByIndex(domain.index(newValue)) // TODO I wanted to put this here, but Scala cannot reolve the right type
}

/** A CategoricalValue variable (which is also a TypedValue), but whose type is specified by a type argument. */
@DomainInSubclasses
abstract trait TypedCategoricalValue[T] extends CategoricalValue {
  this: Variable =>
  type VariableType <: TypedCategoricalValue[T]
  type ValueType = T
  def value: ValueType = domain.get(index) // TODO I'd love to move this to the superclass, but Scala type system is complaining
  override def toString = printName + "(" + (if (value == this) "this" else value.toString + "=") + index + ")"
  // NOTE that "def index" has yet to be defined
}

/** For variables holding a single indexed value, which is not the variable object itself, but a Scala value of type T. */
@DomainInSubclasses
abstract trait TypedCategoricalVariable[T] extends CategoricalVariable with TypedCategoricalValue[T] {
  type VariableType <: TypedCategoricalVariable[T]
  final def set(newValue: ValueType)(implicit d: DiffList) = setByIndex(domain.index(newValue))
  def :=(newValue:ValueType) = set(newValue)(null)
  def value_=(newValue:ValueType) = set(newValue)(null)
} 

/** For variables holding a single, constant indexed value which is of Scala type T. */
@DomainInSubclasses
abstract trait TypedCategoricalObservation[T] extends Variable with TypedCategoricalValue[T] with ConstantValue {
  type VariableType <: TypedCategoricalObservation[T]
}

/** A Variable to hold one of an enumerated set of values of type T, and which does not change.  */
@DomainInSubclasses
abstract class EnumObservation[T](value:T) extends TypedCategoricalObservation[T] {
  type VariableType <: EnumObservation[T]
  final val index = domain.index(value)
}

// TODO get rid of all this "Coordinated" versus non-coordinated.  Everything should just be coordinated.
// It is less efficient, but too error-prone.
// TODO Really?  Verify how much efficiency gain we could get.
// No.  We can't do this.  For example, belief propagation relies on having no coordination

/** A variable whose value is a single indexed value, initialized at construction time; mutable.
    This variable does not, however, hold a trueValue.  For that you should use a Label. */
@DomainInSubclasses
abstract class CoordinatedEnumVariable[T](initialValue:T) extends TypedCategoricalVariable[T] {
  def this() = this(null)
  type VariableType <: CoordinatedEnumVariable[T]
  if (initialValue != null) setByIndex(domain.index(initialValue))(null)
}

/** A kind of _EnumVariable that does no variable value coordination in its 'set' method. 
    This trait abstracts over both EnumVariable and Label, and is used in belief probagation 
    and other places that cannot tolerate coordination. */
trait UncoordinatedCategoricalVariable extends CategoricalVariable with NoVariableCoordination {
  // TODO But this does not absolutely guarantee that some other trait hasn't already overriden set and setByIndex to do coordination!
  // TODO I want some way to tell the compiler that this method should be overriding the CategoricalVariable.set method.
  final override def setByIndex(index: Int)(implicit d: DiffList) = super.setByIndex(index)(d)
}

/**A variable whose value is a single indexed value that does no variable coordination in its 'set' method,  
 * ensuring no coordination is necessary for optimization of belief propagation. 
 * This variable does not hold a trueValue; for that you should use a Label. */
@DomainInSubclasses
abstract class EnumVariable[T](initialValue:T) extends CoordinatedEnumVariable[T](initialValue) with UncoordinatedCategoricalVariable {
  type VariableType <: EnumVariable[T]
}




/*
// TODO We can do better than this now!  It doesn't have to be Categorical
// TODO Perhaps it isn't needed at all, now that we have DiscreteValue?
// TODO Perhaps I should create an IntervalValue, see http://en.wikipedia.org/wiki/Nominal_scale
class IntRangeVariable(low:Int, high:Int) extends TypedCategoricalVariable[Int] {
  type VariableType = IntRangeVariable
  class DomainInSubclasses
  assert(low < high)
  // TODO But note that this will not properly initialize the CategoricalDomain until an instance is created
  if (domain.size == 0) { for (i <- low until high) domain.index(i) }
  assert (domain.size == high-low)
}
// ??? class DiscreteIntervalValue(low:Int, high:Int, bins:Int) extends DiscreteValue {}
*/



// ItemizedObservation support

/** An Observation put into an index, and whose value is the Observation variable itself.  
    For example, you can create 10 'Person extends ItemizedObservation[Person]' objects, 
    and upon creation each will be mapped to a unique integer 0..9.
    p1 = new Person; p1.index == 0; p1.value == p1. */
// Was called ItemizedVariable, but that was the wrong name since its value cannot change.
@DomainInSubclasses
trait ItemizedObservation[This <: ItemizedObservation[This]] extends TypedCategoricalObservation[This] {
  this : This =>
  type VariableType = This
  domain.index(this) // Put the variable in the CategoricalDomain
  val index = domain.index(this) // Remember our own index.  We could save memory by looking it up in the Domain each time, but speed is more important
  override def value = this
}

/** A variable who value is a pointer to an ItemizedObservation.  It is useful for entity-attributes whose value is another variable. */
@DomainInSubclasses
class ItemizedObservationRef[V<:ItemizedObservation[V]] extends TypedCategoricalVariable[V] {
  type VariableType = ItemizedObservationRef[V]
}



/** When mixed in to a CategoricalVariable or CategoricalObservation, the variable's Domain will count the number of calls to 'index'.  
    Then you can reduce the size of the Domain by calling 'trimBelowCount' or 'trimBelowSize', 
    which will recreate the new mapping from categories to densely-packed non-negative integers. 
    In typical usage you would (1) read in the data, (2) trim the domain, (3) re-read the data with the new mapping, creating variables. */
trait CountingCategoricalDomain[This<:CountingCategoricalDomain[This] with CategoricalValues] {
  this: This =>
  type VariableType = This
  type DomainType = CategoricalDomainWithCounter[This]
  class DomainClass extends CategoricalDomainWithCounter[This]
}
