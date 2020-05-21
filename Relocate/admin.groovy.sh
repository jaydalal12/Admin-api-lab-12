#!/bin/bash

echo "**** Start Create Admin *****"
export heap_size_check=50
export max_time_to_check=6

exec groovy -cp "target/*" admin.groovy
