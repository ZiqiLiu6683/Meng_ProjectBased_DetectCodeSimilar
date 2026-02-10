# include <stdio.h>
# include <stdlib.h>
# include "winnow_sim.h"

// Read file and store it into memory
static char* read_file(const char* filePath, size_t* out_len) {
    FILE* f = fopen(filePath, "rb");
    if (!f) {
        perror("Failed to open file");
        return NULL;
    }
    // Get file size
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    // Allocate buffer
    char* buffer = (char*)malloc(n + 1);
    if (!buffer) {
        perror("Failed to allocate buffer");
        fclose(f);
        return NULL;
    }
    // Read file into buffer
    size_t rb = fread(buffer, 1, n, f);
    fclose(f);
    buffer[rb] = '\0';
    if (out_len) {
        *out_len = rb;
    }
    return buffer;
}


double winnow_similarity(const char* fileA, const char* fileB) {
    size_t lenA, lenB;
    char* contentA = read_file(fileA, &lenA);
    char* contentB = read_file(fileB, &lenB);
    if (!contentA || !contentB) {
        free(contentA);
        free(contentB);
        return -1.0;
    }
    printf("Read File A : %s -- %zu bytes\n", fileA, lenA);
    printf("Read File B : %s -- %zu bytes\n", fileB, lenB);
    free(contentA);
    free(contentB);
    return 0.0;
}