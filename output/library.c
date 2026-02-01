#include <stdio.h>
#include <stdlib.h>

// input
int read_int() {
    int r;
    scanf("%d", &r);
    getchar();
    return r;
}

// output
int print_int(int i) {
    printf("%d", i);
    return 0;
}

int print_string(char* string) {
    printf("%s", string);
    return 0;
}

int print_newline() {
    printf("\r\n");
    return 0;
}

int print_true() {
    printf("ano");
    return 0;
}

int print_false() {
    printf("nie");
    return 0;
}


