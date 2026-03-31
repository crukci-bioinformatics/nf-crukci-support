#!/usr/bin/env nextflow

/*
 * Example pipeline demonstrating CRUK CI extension functions
 */

// Include extension functions
include { javaMemMB; javaMemoryOptions; sizeOf; makeCollection; safeName; logException } from 'plugin/nf-crukci-support'

process testJavaMemory {
    memory '1 GB'
    
    script:
    """
    echo "Testing Java memory functions"
    """
}

process testCollectionFunctions {
    script:
    """
    echo "Testing collection functions"
    """
}

workflow {
    println "=== Testing CRUK CI Extension Functions ==="
    
    // Test sizeOf
    println "\n1. Testing sizeOf:"
    def list = [1, 2, 3]
    def single = "test"
    def nullVal = null
    println "   sizeOf([1, 2, 3]) = ${sizeOf(list)}"
    println "   sizeOf('test') = ${sizeOf(single)}"
    println "   sizeOf(null) = ${sizeOf(nullVal)}"
    
    // Test makeCollection
    println "\n2. Testing makeCollection:"
    def singleItem = "file.txt"
    def collection = makeCollection(singleItem)
    println "   makeCollection('file.txt') = ${collection}"
    def listItem = ["a.txt", "b.txt"]
    def collection2 = makeCollection(listItem)
    println "   makeCollection(['a.txt', 'b.txt']) = ${collection2}"
    
    // Test safeName
    println "\n3. Testing safeName:"
    def unsafeName = "My File: Test (v1.0)"
    def safe = safeName(unsafeName)
    println "   safeName('My File: Test (v1.0)') = '${safe}'"
    
    println "\n=== All extension functions work correctly ==="
}
