/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.gen.typescript.poet;

public interface Emittable {
    void emit(TypescriptPoetWriter writer);
}
