import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://crowdcam.co.za").replace(/\/$/, "");
const MAX_VUS = Number.parseInt(__ENV.STRESS_MAX_VUS || "200", 10);
const P95_MS = Number.parseInt(__ENV.STRESS_P95_MS || "1000", 10);
const P99_MS = Number.parseInt(__ENV.STRESS_P99_MS || "2000", 10);

export const options = {
  stages: [
    { duration: "1m", target: Math.max(1, Math.floor(MAX_VUS * 0.05)) },
    { duration: "2m", target: Math.max(1, Math.floor(MAX_VUS * 0.25)) },
    { duration: "2m", target: Math.max(1, Math.floor(MAX_VUS * 0.5)) },
    { duration: "2m", target: MAX_VUS },
    { duration: "1m", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: [`p(95)<${P95_MS}`, `p(99)<${P99_MS}`],
  },
};

const paths = [
  "/",
  "/areas/cape-town",
  "/areas/claremont",
  "/areas/stellenbosch",
  "/login",
];

export default function () {
  const path = paths[Math.floor(Math.random() * paths.length)];
  const response = http.get(`${BASE_URL}${path}`, {
    tags: { page: path },
  });

  check(response, {
    "status is 2xx or 3xx": (res) => res.status >= 200 && res.status < 400,
  });

  sleep(Math.random() * 2 + 1);
}
