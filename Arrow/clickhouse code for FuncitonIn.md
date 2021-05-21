```cpp
struct VoidMapped
{
    template <typename T>
    auto & operator=(const T &)
    {
        return *this;
    }
};
```

---

```cpp
struct ClearableHashSetState
{
    UInt32 version = 1;

    /// Serialization, in binary and text form.
    void write(DB::WriteBuffer & wb) const         { DB::writeBinary(version, wb); }
    void writeText(DB::WriteBuffer & wb) const     { DB::writeText(version, wb); }

    /// Deserialization, in binary and text form.
    void read(DB::ReadBuffer & rb)                 { DB::readBinary(version, rb); }
    void readText(DB::ReadBuffer & rb)             { DB::readText(version, rb); }
};

template <typename Key>
struct FixedClearableHashTableCell{
    using State = ClearableHashSetState;

    using value_type = Key;          // UInt8
    using mapped_type = VoidMapped;
    UInt32 version;
  //....
}

template <typename Key, typename Cell, typename Allocator>
class FixedHashTable : private boost::noncopyable, protected Allocator, protected Cell::State{
  //Cell = FixedClearableHashTableCell
  public:
    using key_type = Key;                           // UInt8
    using mapped_type = typename Cell::mapped_type; // VoidMapped
    using value_type = typename Cell::value_type;   // UInt8
    using cell_type = Cell;

    using LookupResult = Cell *;
    using ConstLookupResult = const Cell *;
  
    Cell * buf; /// A piece of memory for all elements.
  
  LookupResult find(const Key & x) { 
    return !buf[x].isZero(*this) ? &buf[x] : nullptr; 
  }
}


template <typename Key, typename Allocator = HashTableAllocator>
class FixedClearableHashSet : 
   public FixedHashTable<Key, FixedClearableHashTableCell<Key>, Allocator>{
}

/// For the case where there is one numeric key.
/// UInt8/16/32/64 for any types with corresponding bit width.
template <typename FieldType, typename TData, bool use_cache = true> 
struct SetMethodOneNumber
{
    using Data = TData;                   // FixedClearableHashSet<UInt8>
    using Key = typename Data::key_type;  // UInt8
                                          // typename Data::value_type => UInt8 
                                          // FieldType = UInt8
    Data data;                            // FixedClearableHashSet<UInt8>

    using State = ColumnsHashing::HashMethodOneNumber<typename Data::value_type,
        void, FieldType, use_cache>;
};

std::unique_ptr<SetMethodOneNumber<UInt8, FixedClearableHashSet<UInt8>, false>>  key8;
```

---

```CPP

// Derived  = HashMethodOneNumber<Value, Mapped, FieldType, use_cache>
// Value = UInt8
// Mapped = void
// FieldType = UInt8 
template <typename Derived, typename Value, typename Mapped, bool consecutive_keys_optimization>
class HashMethodBase
{
    // Data => FixedClearableHashSet<UInt8>
    template <typename Data>
    FindResult findKey(Data & data, size_t row, Arena & pool)
    {
        auto key_holder = static_cast<Derived &>(*this).getKeyHolder(row, pool);
        return findKeyImpl(keyHolderGetKey(key_holder), data);
    }
}

// Value = UInt8
// Mapped = void
// FieldType = UInt8
template <typename Value, typename Mapped, typename FieldType, bool use_cache = true>
struct HashMethodOneNumber : 
   public columns_hashing_impl::HashMethodBase<
      HashMethodOneNumber<Value, Mapped, FieldType, use_cache>, 
      Value, Mapped, use_cache> 
{
   const char * vec;
        
   FieldType getKeyHolder(size_t row, Arena &) const { 
     return unalignedLoad<FieldType>(vec + row * sizeof(FieldType)); 
   }
}
```

---

```cpp

struct NonClearableSet
{
    /*
     * As in Aggregator, using consecutive keys cache doesn't improve performance
     * for FixedHashTables.
     */
    std::unique_ptr<SetMethodOneNumber<UInt8, FixedHashSet<UInt8>, false >>   key8;
}

template <typename Variant>
struct SetVariantsTemplate: public Variant
{}

using SetVariants = SetVariantsTemplate<NonClearableSet>;
/** Data structure for implementation of IN expression. */
class Set
{
  SetVariants data;
}

/*
case SetVariants::Type::key8:
    executeImpl(*data.key8, key_columns, vec_res, negative, rows, null_map);
    break;
*/
```

---

```cpp
// Method => SetMethodOneNumber
// Method::State =>                   UInt8                 void    UInt8
//            HashMethodOneNumber<typename Data::value_type, void, FieldType, use_cache>;
// method.data => FixedClearableHashSet<UInt8>
template <typename Method, bool has_null_map>
void NO_INLINE Set::executeImplCase(
    Method & method,
    const ColumnRawPtrs & key_columns,
    ColumnUInt8::Container & vec_res,
    bool negative,
    size_t rows,
    ConstNullMapPtr null_map) const
{
    Arena pool;
    typename Method::State state(key_columns, key_sizes, nullptr);

    /// NOTE Optimization is not used for consecutive identical strings.

    /// For all rows
    for (size_t i = 0; i < rows; ++i)
    {
        if (has_null_map && (*null_map)[i])
        {
            if (transform_null_in && has_null)
                vec_res[i] = !negative;
            else
                vec_res[i] = negative;
        }
        else
        {
            auto find_result = state.findKey(method.data, i, pool);
            vec_res[i] = negative ^ find_result.isFound();
        }
    }
}
```