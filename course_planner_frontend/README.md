# Course Planner interface

The React and TypeScript interface provides course search, requirement checklists, semester scheduling, what-if plans and basic plan checks. See the [project README](../README.md) for the Docker demo, screenshots and backend setup.

Use Node.js 24, then run `npm ci` and `npm run dev` from this directory. Vite proxies `/api` to `http://localhost:8080`; set `COURSE_PLANNER_API_URL` to use another backend address. The API and a populated database must be running for catalog and requirement data.

Run `npm test`, `npm run lint` and `npm run build` to validate changes. The tests exercise the pure plan and requirement logic through Node's test runner. Plans are stored in browser local storage by `src/store/PlanStoreProvider.tsx`; the server does not save them.
