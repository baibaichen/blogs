[TOC]

# ClickHouse
语法树的元素（以下为带语义元素的有向无环图）

```C++
class IAST : public std::enable_shared_from_this<IAST>, public TypePromotion<IAST>
{
  
}
```

## C++ 语法

### enable_shared_from_this

来自于 [enable_shared_from_this 的使用及实现原理](http://blog.guorongfei.com/2017/01/25/enbale-shared-from-this-implementaion/)，表明需要在**成员函数**中将 `this` 传递给<u>另一个函数</u>。如果这个函数需要 `shared_ptr<>`，就需要用到这个类。

使用智能指针难以避免的场景之一就是需要把 `this` 当成一个 `shared_ptr` 传递到其他函数 中去。比如你的类实现了某个接口，而你需要把自己注册为该接口的实现。你不能简单粗暴的用 `this` 指针构造一个 `shared_ptr`，因为那样做会导致两个独立的 `shared_ptr` 指向同 一份资源，这对于基于引用计数的 `shared_ptr` 来说是致命的。比如下面的代码。

```c++
class Widget {
    std::shared_ptr<Widget> GetPtr() {
        return shared_ptr<Widget>(this);     // 错误
    }
};

int main() {
    auto widget = std::make_shared<Widget>();
    widget->GetPtr();
}
```

正确的打开方式是继承来自 `std::enable_shared_from_this`，然后调用它的 `shared_from_this` 成员函数。

```C++
class Widget : public std::enable_shared_from_this<Widget> {
    std::shared_ptr<Widget> GetPtr() {
        return shared_from_this();
    }
};

int main() {
    auto widget = std::make_shared<Widget>();
    btn->GetPtr();
}
```

把自己作为基类的模板参数看起来非常诡异，它有一个更诡异的名字——**<u>奇异递归模板模式</u>**。 关于它的使用这里不过多的介绍，有兴趣的同学可以参考《More Effective C++》，《C++ Templates》等书籍。这里主要探讨的是它是如何实现返回自身的智能指针的。

#### 避免多个独立的 shared_ptr 指向同一份资源

在之前的错误代码中，有两个独立的 shared_ptr 指向同一块空间

```C++
shared_ptr<Widget>(this);   // 成员函数中
std::make_shared<Widget>(); // main 函数中
```

很显然 `main` 函数中这这个 shared_ptr 必不可少，所以我们只能去掉 `shared_ptr<Widget>(this)` 而让它共享 main 函数中创建的这个 `shared_ptr`。

#### 如何共享 make_shared 的返回的资源

**实际上，抛开一切来讲，我们想要的**不是基于 `this` 去创建一个 `shared_ptr`，而是希望 `this` 的类型就是 `shared_ptr`。想通了这一点你就会发现，其实我们应该 从 `shared_ptr` 类入手，而不是 `Widget` 类，因为只有 `shared_ptr` 类中 `this` 的类型才是 `shared_ptr`。`make_shared` 会调用 `shared_ptr` 的构造函数，在 `shared_ptr` 的构造函数中，`this` 的类型就是 `shared_ptr`，所以从逻辑上分析，我们甚至可以这样去实现 `shared_from_this` 函数

```C++
// 下面所有的代码省去模板相关的代码，以便阅读。

class shared_ptr<Widget> {
public:
    explicit shared_ptr<Widget>(Widget* w) {
        // 其他初始化，可能在初始化列表中而不是构造函数内部
        RegisterSharedPtr(w, this);
    }
};

void RegisterSharedPtr(Widget* w, shared_ptr<Widget>* sp) {
    // 建立 w 到 sp 的映射关系
}

void GetSharedPtr(Widget* w) {
    // 查找 w 对应的 sp
}

class Widget {
public:
    shared_ptr<Widget> shared_from_this() {
        GetSharedPtr(this);
    }
};
```

#### 建立 w 和 sp 之间的映射关系

建立 w 和 sp 的关系最直接的方式是使用一个 map 来集中管理，**另一种方式就是采用分布式的管理方式，也就是让 w 直接存储这个 sp，而不用借助额外的 map**。

```C++
class shared_ptr<Widget> {
public:
    explicit shared_ptr<Widget>(Widget* w) {
        // 其他初始化
        if (w) {
            w->SetSharedPtr(this);
        }
    }
};

class Widget {
public:
    void SetSharedPtr(shared_ptr<Widget>* sp) {
        sp_ = *sp;
    }

    shared_ptr<Widget> GetPtr() {
        return sp_;
    }

private:
    shared_ptr<Widget> sp_;
};
```

#### 抽象公共基类

上面的设计的最大的问题是，你需要在自己的内部定义一个 `shared_ptr` 类型的成员变量以 及提供一个 `GetPtr` 和 `SetSharedPtr` 成员方法，这些代码和 `Widget` 类的业务逻辑可能没 有太大的关系，而且不适合重用。为了抽象并重用这些代码，我们可以抽象出一个公共的基 类，这也就是 `std::enable_shared_from_this` 实际上做的事情

```C++
class enable_shared_from_this {
public:
    void SetSharedPtr(shared_ptr<Widget>* sp) {
        sp_ = *sp;
    }

    shared_ptr<Widget> GetPtr() {
        return sp_;
    }

private:
    shared_ptr<Widget> sp_;
};

class Widget : public enable_shared_from_this {
};

class shared_ptr<Widget> {
public:
    explicit shared_ptr<Widget>(Widget* w) {
         w->SetSharedPtr(this);
    }
};
```

上面这段代码最大的漏洞在于，`shared_ptr` 是一个模板，它并不知道 `Widget` 类是否继承 自 `enable_shared_from_this`，所以 `w->SetSharedPtr(this)` 这一句的调用不完全正确 。boost 中解决这个问题使用了一个非常巧妙的方法——**重载**，通过重载我们可以让编译器自动选择是否调用 SetSharedPtr。

```C++
class shared_ptr<Widget> {
public:
    explicit shared_ptr<Widget>(Widget* w) {
        SetSharedPtr(this, w);
    }
};

void SetSharedPtr(shared_ptr<Widget>* sp, enable_shared_from_this* w) {
    w->SetSharedPtr(sp);
}

void SetSharedPtr(...) {
    // 什么也不做
}
```

这段代码的精妙，让人叹为观止。

#### 最后一个问题

上面这些代码的逻辑上是正确的，但是实际上还有一个巨大的BUG，那就是 `Widget` 的内部存在一个指向它自己的 `shared_ptr`，这意味着你永远无法销毁掉 `Widget`。销毁 `Widget` 的前提是没有 `shared_ptr` 指向它了，而销毁 `Widget` 必然需要销毁它的成员变量 ，包括指向自身的那个 `shared_ptr`，而它的存在又否定了我们能销毁 `Widget` 的前提——没 有 `shared_ptr` 指向它。这就像是你在画了一个指向自身的箭头，它让你自身形成了循环依 赖，永远没有办法跳出来。

普通的 `shared_ptr` 循环依赖的问题的处理通常使用的是 `weak_ptr`，这一次也不例外。我 们需要存储一个 `weak_ptr` 作为成员变量，而不是 `shared_ptr`，然后在需要的时候通过 weak_ptr 构建出 shared_ptr，所以正确的打开方式是：

```C++
class enable_shared_from_this {
public:
    SetSharedPtr(shared_ptr* sp) {
        wp = sp
    }

    shared_ptr shared_from_this() {
        return shared_ptr(wp);
    }

private:
    weak_ptr wp;
}
```

这是一个接近正确答案的写法，也是一个比较容易理解的方法，但和实际上的写法还一些细 微的差别，比如实际上 `SetSharedPtr` 中并没有把 sp 直接赋值给 wp，而是使用了 shared_ptr 的**别名构造函数**，为什么这么写，个人能力有限，还没弄清楚，弄懂了再回 来补充。

#### 参考
1. [C++ CRTP 简介](https://xr1s.me/2018/05/10/brief-introduction-to-crtp/) 

2. [C++ 惯用法 CRTP 简介](https://liam.page/2016/11/26/Introduction-to-CRTP-in-Cpp/)

3. https://www.jianshu.com/p/ec8a01cba496

4. https://zhuanlan.zhihu.com/p/54945314

5. [The cost of dynamic (virtual calls) vs. static (CRTP) dispatch in C++](https://eli.thegreenplace.net/2013/12/05/the-cost-of-dynamic-virtual-calls-vs-static-crtp-dispatch-in-c)


### is_convertible

[实现 std::is_convertible](https://zhuanlan.zhihu.com/p/98384465)

```C++
template <typename From, typename To>
is_convertible;
```

### enable_if

#### SFINAE

FINAE是英文Substitution failure is not an error的缩写，意思是匹配失败不是错误。这句话什么意思呢？当调用模板函数时编译器会根据传入参数推导最合适的模板函数，在这个推导过程中如果某一个或者某几个模板函数推导出来是编译无法通过的，只要有一个可以正确推导出来，那么那几个推导得到的可能产生编译错误的模板函数并不会引发编译错误。这段话很绕，我们接下来用代码说明一下，一看便知。

```C++
struct Test {
    typedef int foo;
};

template <typename T> 
void f(typename T::foo) {} // Definition #1

template <typename T> 
void f(T) {}               // Definition #2

int main() {
    f<Test>(10); // Call #1.
    f<int>(10);  // Call #2. Without error (even though there is no int::foo) thanks to SFINAE.
}
```
这是wiki上SFINAE的一个经典示例，注释已经解释的相当明白，由于推导模板函数过程中可以找到一个正确的版本，所以即使 `int::foo` 是一个语法错误，但是编译器也不会报错。这就是SFINAE要义。在C++11中，标准确立了这种编译的行为，而不像C++98未明确定义它的行为。通过 `std::enable_if` 和 `SFINAE` 的共同使用，会产生很多很奇妙的实现，STL 库中大量的应用了这种组合，下面我们来看看他们组合一起是如何工作的。

#### 实现

这个模板类的实现相当的简单，看一下一个版本的实现。

```cpp
template<bool B, class T = void> struct enable_if {};

template<class T> struct enable_if<true, T> { typedef T type; };
```

一个普通版本的模板类定义，一个**偏特化版本**的模板类定义。它在第一个模板参数为 `false` 的时候并不会定义 `type`，只有在第一模板参数为 `true` 的时候才会定义 `type`。看一下下面的模板实例化代码

```cpp
typename std::enable_if<true, int>::type t;   // 正确
typename std::enable_if<true>::type;          // 可以通过编译，没有实际用处，
                                              // 推导的模板是偏特化版本，第一模板参数是 true，
                                              // 第二模板参数是通常版本中定义的默认类型即 void
typename std::enable_if<false>::type;         // 无法通过编译，type类型没有定义
typename std::enable_if<false, int>::type t2; // 同上
```

我们可以看到，通过 `typename std::enable_if<bool>::type` 这样传入一个 `bool` 值，就能推导出这个 `type` 是不是未定义的。那么这种用法有什么用途呢？结合上面的SFINAE来看代码

```cpp
template <typename T>
typename std::enable_if<std::is_trivial<T>::value>::type SFINAE_test(T value)
{
    std::cout<<"T is trival"<<std::endl;
}

template <typename T>
typename std::enable_if<!std::is_trivial<T>::value>::type SFINAE_test(T value)
{
    std::cout<<"T is none trival"<<std::endl;
}
```

这两个函数如果是普通函数的话，根据重载的规则是不会通过编译的。即便是模板函数，如果这两个函数都能推导出正确的结果，也会产生重载二义性问题，但是正因为 `std::enable_if` 的运用使这两个函数的返回值在同一个函数调用的推导过程中只有一个合法，遵循SFINAE原则，则可以顺利通过编译。

```cpp
SFINAE_test(std::string("123"));
SFINAE_test(123);
```

推导第一个模板函数的时候:

1. 第一个版本的模板函数 `std::is_trivial<T>::value` 为 `false`，所以未定义类型 `std::enable_if<std::is_trivial<T>::value>::type` ，不能正确推导。

2. 编译器寻找下一个可能的实现，即第二个模板函数，`!std::is_trivial<T>::value` 的值是 `true`，此时 `std::enable_if<!std::is_trivial<T>::value>::type` 是  `void`  类型，推导成功。

这时候 `SFINAE_test(std::string("123"));` 调用有了唯一确定的推导，即第二个模板函数，所以程序打印 <u>T is none trival</u>。与此相似的过程，第二个函数调用打印出 <u>T is trival</u>。

这样写的好处是什么？这个例子中可以认为我们利用SFINAE特性实现了通过不同返回值，相同函数参数进行了函数重载，这样代码看起来更统一一些。还有一些其他应用`std::enable_if`的方式，比如在模板参数列表里，在函数参数列表里，都是利用SFINAE特性来实现某一些函数的选择推导。来看一下cpprefrence上的例子代码


```cpp
#include <type_traits>
#include <iostream>
#include <string>
 
namespace detail { struct inplace_t{}; }
void* operator new(std::size_t, void* p, detail::inplace_t) {
    return p;
}
 
// enabled via the return type
template<class T,class... Args>
typename std::enable_if<std::is_trivially_constructible<T,Args&&...>::value>::type 
    construct(T* t,Args&&... args) 
{
    std::cout << "constructing trivially constructible T\n";
}
 
// enabled via a parameter
template<class T>
void destroy(T* t, 
             typename std::enable_if<std::is_trivially_destructible<T>::value>::type* = 0) 
{
    std::cout << "destroying trivially destructible T\n";
}
 
// enabled via a template parameter
template<class T,
         typename std::enable_if<
             !std::is_trivially_destructible<T>{} &&
             (std::is_class<T>{} || std::is_union<T>{}),
            int>::type = 0>
void destroy(T* t)
{
    std::cout << "destroying non-trivially destructible T\n";
    t->~T();
}
int main()
{
    std::aligned_union_t<0,int,std::string> u;
 
    construct(reinterpret_cast<int*>(&u));
    destroy(reinterpret_cast<int*>(&u));
 
    construct(reinterpret_cast<std::string*>(&u),"Hello");
    destroy(reinterpret_cast<std::string*>(&u));
}
```

可以从代码中看到，上面例子覆盖了基本所有 `std::enable_if` 可以应用的场景，核心就是应用SFINAE原则，来实现调用函数去推导正确的模板函数版本。

---


# 智能指针

> 自动管理指针多引用的情况，在没有引用的时候清空指针和所指对象，类似于Java垃圾回收机制，从而无需手动delete

## 指针概述
> 包含在头文件 #include \<memory\>中，主要为三类指针：  

1. shared_ptr 共享指针/强指针，拥有所指对象的指针，当此指针被销毁时，所指的对象也会被销毁
2. weak_ptr 弱指针，由强指针管理，本身不拥有对象，作用为避免出现指针回路造成的内存泄露（后面讲）
3. unique_ptr 独占指针，一个对象只能被一个unique_ptr拥有，能够转移所有权，超出作用域后释放，但是可以作为return value传递回调用的函数  

## 使用时注意
智能指针不是C++内置的，需要遵循一些原则来保证不会出现错误。  
1. 所指向的对象需要可以通过new/delete来动态申请和销毁，不要将其指向函数栈中的内容。
2. 每个对象只能被一个manager object管理单元管理，因为当一个管理单元销毁时会同时销毁指向的对象，出现多个管理单元时会出现多次delete导致内存泄露。可以(1)在对象创建时就赋值给智能指针。(2)使用make_shared来创建新的shared_ptr指向这个对象。
3. 可以使用get()函数来获取原始的指针，但是不建议这么使用，如果需要转换类型的话可以使用static_cast，后面会讲。  

## Shared_ptr 

同一个对象可被多个shared_ptr指针指向。创建的时候包含一个manager object（管理单元）用来计算指向这个对象的指针个数，同时包含shared count（指向该元素的shared_ptr的个数）和 weak count（指向该元素的weak ptr的个数）  

![](https://raw.githubusercontent.com/acall-deng/acall-deng.github.io/master/_posts/_img/2019-11-12-cpp-smart_ptr/2019-11-12-cpp-shared_ptr_1.png)

### 基本概述
每个shared_ptr在创建的时候会产生一个manager object（管理单元），用来处理引用计数的信息，当shared count为0时对象会被销毁但是管理单元保留，当shared count和weak count同时为0时管理对象也会被销毁

### 代码使用 

当超过作用域的时候 shared_ptr会释放，指向的元素的shared count会减一，当为0时delete指向的对象。  

```cpp
class Thing {
    public:
        void defrangulate();
};
ostream& operator<< (ostream&, const Thing&);
...
// a function can return a shared_ptr
shared_ptr<Thing> find_some_thing();
// a function can take a shared_ptr parameter by value;
shared_ptr<Thing> do_something_with(shared_ptr<Thing> p);
...
void foo()
{
    // the new is in the shared_ptr constructor expression:
    shared_ptr<Thing> p1(new Thing);
    ...
    shared_ptr<Thing> p2 = p1; // p1 and p2 now share ownership of the Thing
    ...
    shared_ptr<Thing> p3(new Thing); // another Thing
    p1 = find_some_thing(); // p1 may no longer point to first Thing
    do_something_with(p2);
    p3->defrangulate(); // call a member function like built-in pointer
    cout << *p2 << endl; // dereference like built-in pointer
    // reset with a member function or assignment to nullptr:
    p1.reset(); // decrement count, delete if last
    p2 = nullptr; // convert nullptr to an empty shared_ptr, and decrement count;
}
```

### 复制对象（3种方式）

```cpp
class Base {};
class Derived : public Base {};
...
shared_ptr<Derived> dp1(new Derived); // 1.创建对象赋值，会出现两个内存申请，一次为Derived对象，一次是shared_ptr的管理单元
shared_ptr<Base> bp1 = dp1;  // 2.直接赋值
shared_ptr<Base> bp2(dp1);  // 3.赋值
shared_ptr<Base> bp3(new Derived);
```

### 使用make_shared优化内存分配

```cpp
shared_ptr<Thing> p(make_shared<Thing>()); // only one allocation!
shared_ptr<Thing> p (make_shared<Thing>(42, "I'm a Thing!"));  // 带参数的 
```
> ==此时需注意==：下面的代码中，`shared_ptr<Base> bp3(new Derived);` 这一句将一个 Derived 对象给了 Base 对象的指针。

这样的后果是：manager object 在 delete 这个对象的时候会调用 Derived 类的析构函数，但是使用get方法获取原始指针的时候会返回Base类的指针。

上述提到的两次申请内存原因是在创建对象先后顺序的不同导致的，但是，在使用了make_shared变为一次申请内存之后，会导致被指向的对象需要在所有的weak_ptr全部销毁之后才会被delete，本来只需要shared count=0就会销毁，延长了对象停留在内存中的时间，在资源敏感型的程序中需要注意：  

- `shared_ptr<Thing> p(new Thing); // 申请了两次内存` 
![](https://raw.githubusercontent.com/acall-deng/acall-deng.github.io/master/_posts/_img/2019-11-12-cpp-smart_ptr/2019-11-12-cpp-shared_ptr_2.png)   

- `shared_ptr<Thing> p(make_shared<Thing>());  // 只申请一次内存`
![](https://raw.githubusercontent.com/acall-deng/acall-deng.github.io/master/_posts/_img/2019-11-12-cpp-smart_ptr/2019-11-12-cpp-shared_ptr_3.png)    

### 替代get方法进行类型转换

```cpp
shared_ptr<Base> base_ptr (new Base);
shared_ptr<Derived> derived_ptr;
// if static_cast<Derived *>(base_ptr.get()) is valid, then the following is valid:
derived_ptr = static_pointer_cast<Derived>(base_ptr);
```

## weak_ptr  

### 创建用法  
只能从 `shared_ptr` 中创建  

```cpp
shared_ptr<Thing> sp(new Thing);
weak_ptr<Thing> wp1(sp); // construct wp1 from a shared_ptr
weak_ptr<Thing> wp2; // an empty weak_ptr - points to nothing
wp2 = sp; // wp2 now points to the new Thing
weak_ptr<Thing> wp3 (wp2); // construct wp3 from a weak_ptr
weak_ptr<Thing> wp4
wp4 = wp2; // wp4 now points to the new Thing.
```

#### 使用 weak_ptr 引用对象  
由于weak_ptr不能直接引用，所以需要使用lock函数取出引用的对象后赋值给shared_ptr变量，在lock取出对象指向该对象在使用完毕之前不会被释放，也可以使用wp.expired()来判断对象是否被释放。   

```cpp
shared_ptr<Thing> sp2 = wp2.lock();  // 取出对象，并且在使用完毕之前不释放这个对象,如果对象已经没了，sp2=false

if(wp.expired()) {  //使用expired方法判断指向的对象是否还存在
```

> ==此时需注意==：在多线程的程序中，判断expired和lock的间隙中对象仍有可能被释放，因此建议使用： expired判断 -> shared_ptr = lock() -> 判断shared_ptr是否为空  

当通过weak_ptr来作为shared_ptr构造函数的参数时，如果weak_ptr已经expired，则会抛出异常bad_weak_ptr

```cpp
void do_it(weak_ptr<Thing> wp){
    shared_ptr<Thing> sp(wp); // construct shared_ptr from weak_ptr
    // exception thrown if wp is expired, so if here, sp is good to go
    sp->defrangulate(); // tell the Thing to do something
}
...
try {
    do_it(wpx);
}
catch(bad_weak_ptr&)
{
    cout << "A Thing (or something else) has disappeared!" << endl;
}
```

### 特殊情况（类内this指针，使用enable_shared_from_this）  

问题代码：  
```cpp
class Thing {
    public:
        void foo();
        void defrangulate();
};

void transmogrify(shared_ptr<Thing>);
int main()
{
    shared_ptr<Thing> t1(new Thing); // start a manager object for the Thing
    t1->foo();
    ...
    // Thing is supposed to get deleted when t1 goes out of scope
}
...
void Thing::foo()
{
    // we need to transmogrify this object
    shared_ptr<Thing> sp_for_this(this); // danger! a second manager object!
    transmogrify(sp_for_this);
}
...
void transmogrify(shared_ptr<Thing> ptr)
{
    ptr->defrangulate();
    /* etc. */
}
```

其中存在的问题是，当调用foo函数时，语句shared_ptr<Thing> sp_for_this(this); 创建了一个新的shared_ptr指向this的类，从而在此段代码中，main函数中的t1和foo函数中的sp_for_this都是指向了同一个Thing对象但是却有两个manager object，从而在析构的时候会出现二次delete导致内存移除，正确的想法是下面这张图，通过使用一个weak_ptr指向不增加shared count.

![](https://raw.githubusercontent.com/acall-deng/acall-deng.github.io/master/_posts/_img/2019-11-12-cpp-smart_ptr/2019-11-12-cpp-weak_ptr_1.png)  

修改后的代码如下所示，在需要使用this指针来创建新ptr的情况下需要继承enable_shared_from_this<Thing>类，从而在函数中使用shared_from_this()来创建一个weak_ptr，进一步获取到其所指的对象。  

```cpp
class Thing : public enable_shared_from_this<Thing> {
    public:
    void foo();
    void defrangulate();
};
int main()
{
    // The following starts a manager object for the Thing and also
    // initializes the weak_ptr member that is now part of the Thing.
    shared_ptr<Thing> t1(new Thing);
    t1->foo();
    ...
}
...
void Thing::foo()
{
    // we need to transmogrify this object
    // get a shared_ptr from the weak_ptr in this object
    shared_ptr<Thing> sp_this = shared_from_this();
    transmogrify(sp_this);
}
...
```

## Unique_ptr  

Unique_ptr所指的对象只能被一个指针指向，该指针指向的内容可以通过（1）std::move函数,(2)函数返回值的方式来转移所有权，但是不能通过复制的方式来获取到对象。例如下面的方法就是不允许的。  

```cpp
unique_ptr<Thing> p1 (new Thing); // p1 owns the Thing
unique_ptr<Thing> p2(p1); // error - copy construction is not allowed.
unique_ptr<Thing> p3; // an empty unique_ptr;
p3 = p1; // error, copy assignment is not allowed.
```

### 使用方法  

1. 通过直接构造的方法获取到一个unique_ptr  

```cpp
void foo ()
{
    unique_ptr<Thing> p(new Thing); // p owns the Thing
    p->do_something(); // tell the thing to do something
    defrangulate(); // might throw an exception
} // p gets destroyed; destructor deletes the Thing
```

2. 通过返回值的方法接收一个unique_ptr并转移所有权  

```cpp
//create a Thing and return a unique_ptr to it:
unique_ptr<Thing> create_Thing()
{
    unique_ptr<Thing> local_ptr(new Thing);
    return local_ptr; // local_ptr will surrender ownership
}
void foo()
{
    unique_ptr<Thing> p1(create_Thing()); // move ctor from returned rvalue
    // p1 now owns the Thing
    unique_ptr<Thing> p2; // default ctor'd; owns nothing
    p2 = create_Thing(); // move assignment from returned rvalue
    // p2 now owns the second Thing
}
```

3. 通过std::move方式转移所有权  

```cpp
unique_ptr<Thing> p1(new Thing); // p1 owns the Thing
unique_ptr<Thing> p2; // p2 owns nothing
// invoke move assignment explicitly
p2 = std::move(p1); // now p2 owns it, p1 owns nothing
// invoke move construction explicitly
unique_ptr<Thing> p3(std::move(p2)); // now p3 owns it, p2 and p1 own nothing
```

### 其他  

1. unique_ptr可以包含在标准的容器里，使用起来没什么不同，移出容器内的指针（erase）或者是清空（clear）都会使得所指向的对象同时被销毁，如果有发生所有权转移的话，容器内可能出现空指针，注意判断。  
2. unique_ptr也有make_unique函数，但是由于它没有manager_object，因此这个操作不会带来任何性能上的提升


## 使用智能指针成环的情况(注意)  

虽然智能指针在很多情况下可以解决动态内存管理的问题，但是仍会出现shared_ptr创建的类成环，导致在出了作用域之后仍然不释放对象的问题。例如：  

```cpp
#include <iostream>
#include <memory>
using namespace std;

class father;
class son;

class father {
public:
    father() {
        cout << "father !" << endl;
    }
    ~father() {
        cout << "~father !" << endl;
    }
    void setSon(shared_ptr<son> s) {
        son = s;
    }
private:
    shared_ptr<son> son;
};


class son {
public:
    son() {
        cout << "son !" << endl;
    }
    ~son() {
        cout << "~son !" << endl;
    }
    void setFather(shared_ptr<father> f) {
        father = f;
    }
private:
    shared_ptr<father> father;
};

void test() {
    shared_ptr<father> f(new father());
    shared_ptr<son> s(new son());
    f->setSon(s);
    s->setFather(f);
}

int main()
{
    test();
    return 0;
}
```

> 此时，在程序结束之后只会出现 Father！ Son！这两个构造函数的输出，两个对象都没有被析构，原因是这两个类里面各有一个shared_ptr强指针相互指着，shared count不为0因此不会被释放。  

解决方案是把其中一个shared_ptr改为weak_ptr即可，例如下面把Father类的ptr改为了弱指针，那么在出了程序的作用域之后，会经过以下几个过程：  
1. test函数中shared_ptr<son> s 和 shared_ptr<father> f 首先销毁。  
   从而son的shared count=0，weak count=1  （father类中的弱指针指着）
   fahter的shared count=1，weak count=0  (son类中的强指针指着)  

2. 由于son中的shared_count = 0,因此son析构，类中的shared_ptr<father> father;也会被销毁，从而fahter的shared count=0，weak count=0，father也被析构  

3. 综上，最后打印出来的语句为  
   father !  
   son !  
   \~son !  
   \~father !  
   

```cpp
class father {
public:
    father() {
        cout << "father !" << endl;
    }
    ~father() {
        cout << "~father !" << endl;
    }
    void setSon(shared_ptr<son> s) {
        son = s;
    }
private:
    //shared_ptr<son> son;
    weak_ptr<son> son; // 用weak_ptr来替换
};
```

## 最后

综上可以看出，虽然指针可以避免大部分的问题，但是例如成环的情况。上例较为简单，一开始可以预见成环从而使用弱指针，但在程序较复杂规模较大的情况下是仍有可能出现问题的，使用时还是需要注意。  

## `intrusive_ptr`: Managing Objects with Embedded Counts

The `intrusive_ptr` class template stores a pointer to an object with an embedded reference count. Every new `intrusive_ptr` instance increments the reference count by using an unqualified call to the function `intrusive_ptr_add_ref`, passing it the pointer as an argument. Similarly, when an `intrusive_ptr` is destroyed, it calls `intrusive_ptr_release`; this function is responsible for destroying the object when its reference count drops to zero. The user is expected to provide suitable definitions of these two functions. On compilers that support argument-dependent lookup, `intrusive_ptr_add_ref` and `intrusive_ptr_release` should be defined in the namespace that corresponds to their parameter; otherwise, the definitions need to go in namespace `boost`. The library provides a helper base class template `intrusive_ref_counter` which may help adding support for `intrusive_ptr` to user types.

The class template is parameterized on `T`, the type of the object pointed to. `intrusive_ptr<T>` can be implicitly converted to `intrusive_ptr<U>` whenever `T*` can be implicitly converted to `U*`.

The main reasons to use `intrusive_ptr` are:

- Some existing frameworks or OSes provide objects with embedded reference counts;
- The memory footprint of `intrusive_ptr` is the same as the corresponding raw pointer;
- `intrusive_ptr<T>` can be constructed from an arbitrary raw pointer of type `T*`.

As a general rule, if it isn’t obvious whether `intrusive_ptr` better fits your needs than `shared_ptr`, try a `shared_ptr`-based design first.

> 使用侵入式计数器管理对象
>
> `intrusive_ptr` **<u>类模板</u>**存储指向带有**<u>==嵌入式引用计数器对象==</u>**的指针。每个新的 `intrusive_ptr` 实例都会增加引用计数，方法是通过无条件调用 `intrusive_ptr_add_ref`，并将指针作为参数传递给它来实现。类似地，当销毁一个 `intrusive_ptr`，要调用 `intrusive_ptr_release`，该函数负责在对象的引用计数降至零时销毁该对象。用户应正确定义这两个函数。如果是支持**==依赖于实参名字查找（[ADL](https://zh.wikipedia.org/wiki/%E4%BE%9D%E8%B5%96%E4%BA%8E%E5%AE%9E%E5%8F%82%E7%9A%84%E5%90%8D%E5%AD%97%E6%9F%A5%E6%89%BE)）==**的编译器， `intrusive_ptr_add_ref` 和 `intrusive_ptr_release` 要在和**参数相对应的命名空间**中定义。否则，在 `boost` 命名空间中定义它们。用该库提供的一个辅助基类模板  `intrusive_ref_counter`，可简化用户类型对 `intrusive_ptr` 的支持。
>
> 类模板的参数是 `T`，即**指针的对象类型**。 `T *` 可隐式转换为 `U *`， `intrusive_ptr <T>` 也可以隐式转换为 `intrusive_ptr <U>`。
>
> 使用`intrusive_ptr`的主要原因是：
>
> - 一些现有的框架或操作系统为对象提供嵌入式引用计数；
> - `intrusive_ptr`的内存占用量与相应的原始指针相同；
> - 可以从 `T *`  类型任意的原始指针构造出 `intrusive_ptr <T>`。
>
> 通常，如果尚不明确 `intrusive_ptr` 是否比 `shared_ptr` 更适合您的需求，从 `shared_ptr` 开始尝试。
>
> > 1. [C++ Core Guidelines: Surprises with Argument-Dependent Lookup](https://www.modernescpp.com/index.php/c-core-guidelines-argument-dependent-lookup-or-koenig-lookup)

## `intrusive_ref_counter`

The `intrusive_ref_counter` class template implements a reference counter for a derived user’s class that is intended to be used with `intrusive_ptr`. The base class has associated `intrusive_ptr_add_ref` and `intrusive_ptr_release` functions which modify the reference counter as needed and destroy the user’s object when the counter drops to zero.

The class template is parameterized on `Derived` and `CounterPolicy` parameters. The first parameter is the user’s class that derives from `intrusive_ref_counter`. This type is needed in order to destroy the object correctly when there are no references to it left.

The second parameter is a policy that defines the nature of the reference counter. The library provides two such policies: `thread_unsafe_counter` and `thread_safe_counter`. The former instructs the `intrusive_ref_counter` base class to use a counter only suitable for a single-threaded use. Pointers to a single object that uses this kind of reference counter must not be used in different threads. The latter policy makes the reference counter thread-safe, unless the target platform doesn’t support threading. Since in modern systems support for threading is common, the default counter policy is `thread_safe_counter`.

> `intrusive_ref_counter` 类模板为要与 `intrusive_ptr` 一起使用的用户类实现了一个引用计数器基类。这个基类有对应的 `intrusive_ptr_add_ref` 和 `intrusive_ptr_release` 函数，它们会按需修改引用计数器，并在计数器降至零时销毁用户的对象实例。
>
> 类模板有两个模板参数：`Derived` 和 `CounterPolicy`。第一个是 `intrusive_ref_counter` 派生类的**<u>==类型==</u>**。引用计数为零时，使用此类型来正确地销毁该对象。
>
> 第二个是策略参数，定义引用计数器性质。该库提供了两个这样的策略：`thread_unsafe_counter`和 `thread_safe_counter`。前者指示`intrusive_ref_counter` 基类使用==只适合单线程使用的计数器==，不得在不同的线程中使用指向这种引用计数器的对象指针。后一种策略是使用线程安全的引用计数器（除非目标平台不支持线程）。由于现代系统普遍都支持线程，因此 `thread_safe_counter` 是默认的计数器策略。

## 参考   

- Using C++11’s Smart Pointers (David Kieras, EECS Department, University of Michigan)  
- <a href="https://blog.csdn.net/love_hot_girl/article/details/21161507"> CSDN: std::make_shared有啥用 </a>  
- <a href="https://blog.csdn.net/gcs6564157/article/details/70144846"> 用weak_ptr解决shared_ptr的环形引用问题 </a>

