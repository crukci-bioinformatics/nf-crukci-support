#!/bin/sh

notabs4 $(find . -name "*.nf" -or -name "*.config" | grep -v .git)
notabs4 README.md
