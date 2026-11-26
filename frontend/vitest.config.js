import {defineConfig} from 'vitest/config';

// Bound parallel JSDOM workers; lazy portal imports need a larger budget on CI
// and when the Java regression suite runs on the same development machine.
export default defineConfig({
  test: {
    pool: 'threads',
    maxWorkers: 2,
    testTimeout: 20000,
    hookTimeout: 20000,
    setupFiles: ['./src/test-setup.js'],
  },
});
