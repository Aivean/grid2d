Implementation
===

I was not able to find any existing data structure whose implementation exactly matches this one. 
Please let me know if you are able do find it. The closest data structures are:
* [radix tree](https://en.wikipedia.org/wiki/Radix_tree)
* [hash array mapped trie](https://en.wikipedia.org/wiki/Hash_array_mapped_trie)
* [QuadTree](https://en.wikipedia.org/wiki/Quadtree)
* [Judy array](https://en.wikipedia.org/wiki/Judy_array)


### General idea
 
* data structure is a tree/trie with branching factor of 32 (2<sup>5</sup>)
* 32 bit key is split into 5-bit chunks
* each node is tied to a specific 5-bit portion of the key space and maps 
    each of its 32 children to a specific value of its portion of the key    
* chunks from first and second key dimension are interleaved down the node hierarchy, i.e.:
    * consider key {X, Y}, where X and Y are 32-bit ints
    * let X<sub>0</sub>..X<sub>7</sub> and Y<sub>0</sub>..Y<sub>7</sub> be X and Y key components split into 5-bit chunks,
    where X<sub>0</sub> represents lower five bits of X and X<sub>7</sub> represents higher two bits of X + zero padding 
    * in this scenario, tree levels starting from the root (assuming full occupancy) would address following key chunks:
        * lvl<sub>0</sub> (root): X<sub>7</sub>
        * lvl<sub>1</sub> : Y<sub>7</sub>
        * lvl<sub>2</sub> : X<sub>6</sub>
        * lvl<sub>3</sub> : Y<sub>6</sub>
        * lvl<sub>4</sub> : X<sub>5</sub>
        * ...
        * lvl<sub>12</sub> :  X<sub>1</sub>
        * lvl<sub>13</sub> : Y<sub>1</sub>
        * lvl<sub>14</sub> :  X<sub>0</sub>
        * lvl<sub>15</sub> :  Y<sub>0</sub>
    * children of lvl<sub>15</sub> are actual `T` values

### Optimizations

General idea above has obvious flaws in terms of performance and memory consumption. 
To rectify them several optimizations were done.

1. Key space compression

    As described in "Intended usage", keys are expected to be closely clustered together.
    Internally stored keys are translated into the integer space that is 
    positive and is close to zero. This is done by the pair of internally maintained integer shifts.
    
2. Depth compression

    Storing the 16 levels needed for the full keyspace is not optimal both in terms of space and performance.
    "Key space compression" reduces the key space to the actually occupied range,
    and the actual hierarchy depth is only as deep as necessary to accommodate the new reduced key space.
    
    For example, to store elements in the key range of 0..100 only two levels (or four levels in 2 dimesnions) 
    are required.
    
    Tree depth is automatically adjusted when the new elements are added and removed. 
    I.e. the following invariant is preserved: when there are more than two levels in the tree,
    top two levels (root and its child) must have at least one and two elements each, otherwise these levels are removed and 
    the tree depth is reduced by two.  

3. Node compression

    Storing 32-element array on every level is wasteful in terms of space,
    especially for sparse data. When a node has two or less children, they are
    stored in a fixed two-element array and their keys are stored alongside in a separate field. 

4. Caching
    
    Sequential access is one of the things that this data structure is optimized for.
    Primary use case is row-major access order (i.e. A[0,0], A[0,1], A[0,2], A[1,0], A[1,1]...).
    
    For every access, the pointer to the lowest level of the hierarchy is cached. 
    This way, if next access happens to be in the close horizontal vicinity (5-bit address range) 
    of the last access, no CPU cycles are wasted to traverse the whole hierarchy,
    moreover, all accessed memory is (hopefully) already in the CPU cache. 
    
    Due to this caching, this data structure is not thread-safe, even for reading. 
    To read the data simultaneously from multiple threads, read-only view must be created 
    for each thread (See [Grid.java](core/src/main/java/com/aivean/grid2d/Grid.java) header for details). 

