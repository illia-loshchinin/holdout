import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    // A 1,000-member cohort paged 10 at a time is slow but must not be skipped
    // (AC-7, tasks T076).
    testTimeout: 120_000,
    hookTimeout: 120_000,
    include: [
      'scenarios/**/*.spec.ts',
      'invariants/**/*.spec.ts',
      'helpers/**/*.spec.ts',
      'smoke/**/*.spec.ts',
      // The fixture generator runs with the suite on purpose: CI's
      // `git diff --exit-code -- fixtures/` is what catches a layout, a seed,
      // an account order or a derived figure moving in any of the three
      // languages without the fixtures being regenerated (T029, contracts §7).
      'fixtures/generate.ts',
    ],
    reporters: ['default'],
  },
});
