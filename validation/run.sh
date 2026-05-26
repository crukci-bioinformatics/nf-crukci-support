#!/bin/bash

# Launch the clean pipeline. Copy this to a clean directory to run it.
# Might need to change the path to the pipeline if not run in the
# working directory (which of course would be a good thing!).

rm -rf .nextflow* work
nextflow run .
