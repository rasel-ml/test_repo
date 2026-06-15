<h1 align="center">Learn Kotlin</h1>

### Topic 1 : Printing Output
`print()` prints text without moving to a new line.


```kotlin
print("Hello ")
print("World")
```
Output:
    Hello World

`println()` prints text in the console and moves to the next line.


```kotlin
println("Hello")
println("World")
```

    Hello
    World


`println()` with no argument prints a blank line.


```kotlin
println("Line 1")
println()
println("Line 3")
```

    Line 1
    
    Line 3


#### Escaped Charecters
New line (`\n`) Tab (`\t`)


```kotlin
println("Name\tAge")
println("Rasel\t21")
```

    Name	Age
    Rasel	21


#### Raw Strings


```kotlin
println("""
Line 1
Line 2
Line 3
""")
```

    
    Line 1
    Line 2
    Line 3
    


### Topic 2 : Variables
Kotlin has two main ways to store values:
- `val` = cannot change
- `var` = can change

Use `val` when the value should stay fixed. Use `var` when the value may change later.


```kotlin
val a = 67
var b = 89
println("b is $b")
b = a + b
println ("b is now $b")
```

    b is 89
    b is now 156


### Topic 3 : Oparators
Operators let you perform calculations and comparisons.
#### Arithmetic Operators
|Oparator|Meaning|
|---|--|
|+|Add|
|-|Subtract|
|*|Multiply|
|/|Divide|
|%|Modulus (Reminder)|


```kotlin
val a = 10
val b = 3

println(a + b) // 13
println(a - b) // 7
println(a * b) // 30
println(a / b) // 3
println(a % b) // 1
```

    13
    7
    30
    3
    1


#### Assignment Operators
|Oparator|Meaning|
|---|--|
|=|Assign|
|+=|Add and assign|
|-=|Subtract and assign|
|*=|Multiply and assign|
|/=|Divide and assign|


```kotlin
var x = 10

x += 5
println(x) // 15

x -= 3
println(x) // 12
```

    15
    12


#### Comparison Operators
Used to compare values.
|Oparator|Meaning|
|---|--|
|==|Equal|
|!=|Not equal|
|>|Greater than|
|<|Less than|
|>=|Greater or equal|
|<=|Less or equal|


```kotlin
val a = 10
val b = 20

println(a == b) // false
println(a < b)  // true
println(a >= b) // false
```

    false
    true
    false


Result is always a Boolean (true or false).
#### Logical Operators
Used to combine conditions.
|Oparator|Meaning|
|---|--|
|&&|AND|
|\|\||OR|
|!|NOT|
##### AND (&&)
Both condition must be true.


```kotlin
val age = 20
val hasId = true

println(age >= 18 && hasId)
```

    true


##### OR(||)
Atleast one condition must be true.


```kotlin
val isAdmin = false
val isOwner = true

println(isAdmin || isOwner)
```

    true


##### NOT (!)
Reverses a Boolean.


```kotlin
val loggedIn = true

println(!loggedIn)
```

    false


#### Increment and Decrement
|Oparator|Meaning|
|---|--|
|++|Increase by 1|
|--|Decrease by 1|


```kotlin
var x = 5

x++
println(x) // 6

x--
println(x) // 5
```

    6
    5


#### Operator Precedence
Just like mathematics:


```kotlin
val result = 2 + 3 * 4
println(result)
```

    14


Use parentheses:


```kotlin
val result = (2 + 3) * 4
println(result)
```

    20


### Topic 4 : Data Types
Common Kotlin data types:
- `Int` - whole numbers
- `Double` - decimal numbers
- `Boolean` - true/false
- `String` - text
- `Char` - single character

Kotlin can often detect the type automatically, but you can also write it clearly.


```kotlin
val count: Int = 5
val price: Double = 99.5
val isReady: Boolean = true
val city: String = "Khulna"
val grade: Char = 'A'
```

### String Templates
You can put variables inside strings using `$`.


```kotlin
val name = "Rasel"
val age = 21

println("My name is $name and I am $age years old.")
```

    My name is Rasel and I am 21 years old.


You can also use expressions:


```kotlin
println("Next year I will be ${age + 1}")
```

    Next year I will be 22


#### Formatted Output (printf style)
Common format specifiers:
|Specifier|Type|
|---|---|
|%d|Integer|
|%f|Decimal number|
|%s|String|
|%c|Character|
|%b|Boolean|
|%n|New line|


```kotlin
val pi = 3.14159
System.out.printf("PI = %.2f%n", pi)
```

    PI = 3.14





    java.io.PrintStream@5638520b



### If / Else Statement
Used for decision making.


```kotlin
val age = 18

if (age >= 18) {
    println("Adult")
} else {
    println("Minor")
}
```

    Adult


we can also use **Else If** when there are multiple conditions.


```kotlin
val marks = 75

if (marks >= 80) {
    println("A+")
} else if (marks >= 70) {
    println("A")
} else if (marks >= 60) {
    println("A-")
} else {
    println("Fail")
}
```

    A


We can use nested if/else if neccesary


```kotlin
//upcoming
```

Kotlin checks conditions from top to bottom and runs the first match.

### When Statement
when is like a better switch


```kotlin
val day = 3

when (day) {
    1 -> println("Sunday")
    2 -> println("Monday")
    3 -> println("Tuesday")
    4 -> println("Wednesday")
    else -> println("Invalid day")
}
```

    Tuesday


Use when when one value needs to match many possible cases.
### Loops
#### For loop
we use loop when we need like to prints numbers from 1 to 5.


```kotlin
for (i in 1..5) {
    println(i)
}
```

    1
    2
    3
    4
    5


You can also loop through a list:


```kotlin
val names = listOf("A", "B", "C")

for (name in names) {
    println(name)
}
```

    A
    B
    C


#### While loop
The loop keeps running while the condition is true.


```kotlin
var i = 1

while (i <= 5) {
    println(i)
    i++
}
```

    1
    2
    3
    4
    5


### Functions
Functions help you reuse code.
`fun` means function. Calling `greet()` runs it.


```kotlin
fun greet() {
    println("Hello!")
}

greet()
```

    Hello!


Function with parameters
A parameter is input sent into the function.


```kotlin
fun greet(name: String) {
    println("Hello, $name")
}

greet("Rasel")
```

    Hello, Rasel


Function with return value
This function returns a number. The return type is written after :


```kotlin
fun add(a: Int, b: Int): Int {
    return a + b
}

val result = add(5, 3)
println(result)
```

    8


### Null Safety
Kotlin is very strong here.
A variable that can be empty must be marked `?`.


```kotlin
var name: String? = "Rasel"
```

You must check before using it safely:


```kotlin
if (name != null) {
    println(name.length)
}
```

    5


Or use safe call:


```kotlin
println(name?.length)
```

    5


This helps prevent crashes caused by null values
### Lists
A list stores multiple items.


```kotlin
val fruits = listOf("Apple", "Banana", "Mango")
println(fruits[0])
```

    Apple


`listOf()` creates a read-only list.
If you want to change items, use `mutableListOf()`:


```kotlin
val numbers = mutableListOf(1, 2, 3)
numbers.add(4)
println(numbers)
```

    [1, 2, 3, 4]


### Arrays
Arrays also store multiple values. Arrays are fixed-size containers. In Kotlin, lists are more common than arrays.


```kotlin
val nums = arrayOf(10, 20, 30)
println(nums[1])
```

    20


### Classes and Objects
Kotlin supports object-oriented programming.
A class is a blueprint. An object is a real thing made from that blueprint.


```kotlin
class Person(val name: String, val age: Int)

val p1 = Person("Rasel", 21)
println(p1.name)
```

    Rasel


### Constructor
This is the part inside the class definition:


```kotlin
class Student(val name: String, val roll: Int)
```

When you create the object, you pass values into the constructor.


```kotlin
val s = Student("Rasel", 9)
```

### Object and Companion Object
Sometimes you do not need to create an object to use a function.


```kotlin
class MathHelper {
    companion object {
        fun square(n: Int): Int {
            return n * n
        }
    }
}

println(MathHelper.square(4))
```

    16


A companion object acts like a static member in Java.

### Basic Input Example
In Kotlin console programs:
readLine() reads text from the user.


```kotlin
print("Enter your name: ")
val name = readLine()
println("Hello, $name")
```

    Enter your name: 

    stdin: Rasel


    Hello, Rasel


### Type Conversion
Sometimes you need to convert one type to another.


```kotlin
val a = "10"
val b = a.toInt()

println(b + 5)
```

    15


The string "10" becomes the number 10.
Common conversions:


```kotlin
val x = 5.toDouble()
val y = 9.8.toInt()
```

### Range
A range is a sequence of values.


```kotlin
for (i in 1..3) {
    println(i)
}
```

    1
    2
    3


`1..3` means 1 to 3.
You can also go backwards:


```kotlin
for (i in 5 downTo 1) {
    println(i)
}
```

    5
    4
    3
    2
    1