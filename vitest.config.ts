import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/**/*.test.ts'],
    environment: 'node',
    clearMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.ts'],
      reporter: ['text-summary'],
      /**
       * Set a little under where the suite actually runs, not at it.
       *
       * A threshold pinned to the current number turns every honest refactor
       * into a red build, and a team that has to raise the bar to land a
       * change stops reading the bar at all. A few points of headroom catches
       * a module that arrives untested while letting the ordinary week
       * through.
       */
      thresholds: {
        statements: 95,
        branches: 90,
        functions: 95,
        lines: 95,
      },
    },
  },
});
