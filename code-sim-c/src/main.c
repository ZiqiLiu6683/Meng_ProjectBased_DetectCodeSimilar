# include <stdio.h>
# include "winnow_sim.h"

int main(int argc, char* argv[]) {
    if (argc < 3) {
        printf("Usage: ./code-sim-c <fileA> <fileB>\n");
        return 1;
    }

    double similarity = winnow_similarity(argv[1], argv[2]);
    printf("Similarity: %.2f\n", similarity);
    return 0;
}
