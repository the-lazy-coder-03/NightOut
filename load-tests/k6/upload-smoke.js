import encoding from "k6/encoding";
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = (__ENV.BASE_URL || "https://crowdcam.co.za").replace(/\/$/, "");
const UPLOAD_PATH = __ENV.UPLOAD_PATH;

export const options = {
  vus: Number.parseInt(__ENV.UPLOAD_VUS || "1", 10),
  iterations: Number.parseInt(__ENV.UPLOAD_ITERATIONS || "1", 10),
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<5000"],
  },
};

const onePixelPng = encoding.b64decode(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=",
  "rawstd",
);

export default function () {
  if (!UPLOAD_PATH) {
    throw new Error("Set UPLOAD_PATH, for example /clubs/halo/dates/2026-08-17/upload");
  }

  const form = {
    photos: http.file(onePixelPng, `k6-smoke-${Date.now()}-${__VU}.png`, "image/png"),
  };

  const response = http.post(`${BASE_URL}${UPLOAD_PATH}`, form, {
    headers: {
      "X-CrowdCam-Batch-Upload": "true",
    },
  });

  check(response, {
    "upload accepted": (res) => res.status >= 200 && res.status < 300,
    "json response": (res) => String(res.headers["Content-Type"] || "").includes("application/json"),
  });

  sleep(1);
}
