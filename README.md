Grid2d
===

Two-dimensional grid data structure optimized for both dense and semi-sparse data,
like `QuadTree` with `(int32, int32)` keys, but faster for dense data, see below.


### Key points

* API and behavior is similar to `QuadTree<T>` (or `Map<Pair<Integer, Integer>, T>` with added AABB queries):
    * `T get(int i, int j)` get single value by key  
    * `void set(int i, int j, T value)` set single value by key
    * `void query(int i0, int j0, int i1, int j1, QueryFun<T> cb)` query AABB region
    
* `get` and `set` performance is `O(1)` (for 32 bit keys) and it's *fast*:
    * as fast as "plain 2d array" for random access  (≈20ns/op)
    * only 2-3 times slower than "plain 2d array" for sequential (cache-friendly)
     access over dense data (≈11ns vs ≈3.6ns per op)
    * 2x-4x faster than radix/quad/critbit trees and `HashMap` for random access 
        (≈20ns vs 50-80ns per op)
* AABB queries are:
   * same or faster than sequentially accessing plain 2d array on any data density
        (when querying square region)
       * where "data density" is `n_stored_elements / (key_range_x * key_range_y)`
   * compared to `QuadTree`:
       * faster than `QuadTree` on dense data (density range [0.1, 1])  
       * same performance on data density range [0.01, 0.1]
       * 2x slower on data density 10<sup>-4</sup>
       * 3x slower on data density 10<sup>-7</sup>
       * 4x slower on data density 10<sup>-9</sup>
       
* memory footprint is `O(N)` for both sparse and dense data (where N is the number of values stored)
* best results are achieved when keys are clustered together (multiple clusters are OK, but single tight cluster is optimal) 

### Intended usecase

This data structure was specifically designed to index in-memory world map in 2d games. 
"World map" here is a map of int 2d coordinates into a tile object. 

Active world map is a dense or semi-dense set of tiles with integer 2d coordinates, clustered in relatively small region,
say (1000 x 1000, depending on active world map size). 
However, depending on the player's location in the bigger world, coordinates, being an absolute values, can 
be clustered around any integer from 32-bit space.

Common operations include: 
* get/set single tile by specific coordinates
* get neighbors of a specific coordinates
* get a rectangular region (AABB) of tiles (or all indexed tiles), as in for batch processing
* add a chunk of new tiles to the index
* remove a chunk of tiles from the index 

Being a tradeoff between `QuadTree` and a "plain 2d array" in terms of performance, 
this data structure is better than both of them for this specific set of requirements.  


### Benchmarks

See comments inside the [GridBenchmark](src/jmh/java/com/aivean/grid2d/GridBenchmark.java).

### Implementation

See [implementation page](doc/implementation.md).


### Usage

Hosted on Bintray: https://bintray.com/aivean/grid2d/grid2d

To add as a maven/gradle dependency: 

* specify the repository url:  https://dl.bintray.com/aivean/grid2d
* add dependency: `"com.aivean.grid2d:grid2d:$VERSION"`


### Building and testing

* `./gradlew clean build` build and run tests
* `./gradlew cleanTest test` run tests
* `./gradlew jmh` run all benchmarks
* `./gradlew jmh -PjmhInclude=plainArray` run benchmarks matching the name mask


### Publishing

(a note to myself)

    ./gradlew bintrayUpload -Dbintray.user=aivean -Dbintray.key=...
    
    

### Copyright

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Ivan Zaitsev © 2020
