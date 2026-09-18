import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    // A 1,000-member cohort paged 10 at a time is slow but must not be skipped
    // (AC-7, tasks T076).
    testTimeout: 120_000,
    hookTimeout: 120_000,
    include: ['scenarios/**/*.spec.ts', 'invariants/**/*.spec.ts', 'smoke/**/*.spec.ts'],
    reporters: ['default'],
  },
});
