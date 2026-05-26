#!/bin/bash
#SBATCH --no-requeue
#SBATCH -p general
#SBATCH -J "Nextflow Alignment"
#SBATCH --nodes 1
#SBATCH --mem 2G
#SBATCH --mincpus 1
#SBATCH --time 2-00:00:00
#SBATCH --open-mode truncate
#SBATCH -o nextflow.out

PROFILE=standard

case $(hostname -s) in
    bioinf-srv008)
        PROFILE=bioinf
        ;;
    clust1-headnode-?|clust1-node-*)
        PROFILE=cluster
        ;;
esac

rm -rf .nextflow.log*

nextflow run alignment.nf -resume -profile $PROFILE --aligner="$1" --endType="$2"
