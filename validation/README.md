## CRUK-CI Over Resource Pipeline

This pipeline simply runs tasks that ask use more memory than they request or
take more time than they've declared. It is used to help develop the CRUK-CI
support plugin for dealing with Slurm failures, where jobs being killed because
of memory overruns are not detected by the standard Nextflow mechanisms.
