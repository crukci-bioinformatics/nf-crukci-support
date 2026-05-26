#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <unistd.h>

int main (int argc, char** argv)
{
    size_t toGrab = 32;
    uint32_t toWait = 4;

    if (argc > 1)
    {
        toGrab = atoi(argv[1]);
    }
    if (argc > 2)
    {
        toWait = atoi(argv[2]);
    }

    size_t toGrabBytes = toGrab << 20;

    uint32_t* grabbed = (uint32_t*)malloc(toGrabBytes);
    if (grabbed)
    {
        printf("Grabbed %lu bytes (%lu megabytes). Filling the memory so it is accessed.\n", toGrabBytes, toGrab);

        uint32_t intSize = sizeof(uint32_t);
        size_t iterations = toGrabBytes / intSize;
        for (size_t i = 0; i < iterations; i++)
        {
            grabbed[i] = 0;
        }

        printf("Holding for %u seconds.\n", toWait);
        sleep(toWait); /* Wait for something to happen. */
        free(grabbed);
        return 0;
    }
    else
    {
        printf("Failed to grab %lu bytes (%lu megabytes).\n", toGrabBytes, toGrab);
        return 1;
    }
}
