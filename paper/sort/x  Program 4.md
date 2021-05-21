```C
// Program 4.1. In-place permutation to substitute in Program 3.1.

#define swap (p, q, r)  r = p, p = q, q = r
string           r, t;
int              k, c;
for(k=0; k < n; ) {
  r = a[k];                     // 图 4.2
  for(;;) {
    c = r[b];                   // 图 4.3
    if(--pile[c] <= a+k)
      break;
    swap(*pile[c], r, t);
  }
  a[k] = r;                     // 图 4.4
  K += count[c];
  count[c] = 0;
}
```

