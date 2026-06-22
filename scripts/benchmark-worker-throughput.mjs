#!/usr/bin/env node
import http from "node:http";
import { writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const args = parseArgs(process.argv.slice(2));

const backendUrl = arg("backend-url", "http://localhost:8080").replace(/\/$/, "");
const username = arg("username", "admin");
const password = arg("password", "admin");
const instances = intArg("instances", 100);
const concurrency = intArg("concurrency", 20);
const durationSeconds = optionalIntArg("duration-seconds");
const mockPort = intArg("mock-port", 19090);
const mockDelayMs = intArg("mock-delay-ms", 250);
const pollIntervalMs = intArg("poll-interval-ms", 250);
const timeoutSeconds = intArg("timeout-seconds", 180);
const securityDisabled = boolArg("security-disabled", false);
const externalUrl = arg("external-url", `http://host.docker.internal:${mockPort}/mock-api`);
const processId = arg("process-id", `benchmark-api-task-${Date.now()}`);
const outputFile = args.get("output-file");

let mockRequestCount = 0;

const mockServer = http.createServer((req, res) => {
  if (req.url?.startsWith("/mock-api")) {
    mockRequestCount += 1;
    setTimeout(() => {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({ status: "OK", handledAt: new Date().toISOString() }));
    }, mockDelayMs);
    return;
  }

  res.writeHead(404, { "content-type": "application/json" });
  res.end(JSON.stringify({ error: "not_found" }));
});

await listen(mockServer, mockPort);

try {
  console.log(`Easy BPM worker benchmark`);
  console.log(`Backend: ${backendUrl}`);
  console.log(`Mock API: http://0.0.0.0:${mockPort}/mock-api (${mockDelayMs}ms delay)`);
  console.log(`Worker-facing URL in process: ${externalUrl}`);
  console.log(
    durationSeconds
      ? `Duration: ${durationSeconds}s, submit concurrency: ${concurrency}`
      : `Instances: ${instances}, submit concurrency: ${concurrency}`
  );

  const token = securityDisabled ? null : await login();
  const processDefinition = buildProcessDefinition(processId, externalUrl);
  const deployed = await request("POST", "/processes", processDefinition, token);
  const deployedKey = deployed.key || deployed.processId || processId;

  const startWall = performance.now();
  const results = durationSeconds
    ? await runForDuration(deployedKey, token, durationSeconds, concurrency)
    : await runPool(
        Array.from({ length: instances }, (_, index) => index),
        concurrency,
        async (index) => runInstance(deployedKey, token, index)
      );
  const totalMs = performance.now() - startWall;

  const completed = results.filter((r) => r.status === "COMPLETED");
  const failed = results.filter((r) => r.status === "FAILED");
  const other = results.filter((r) => r.status !== "COMPLETED" && r.status !== "FAILED");
  const latencies = completed.map((r) => r.latencyMs).sort((a, b) => a - b);

  const summary = {
    processId: deployedKey,
    requestedInstances: durationSeconds ? null : instances,
    durationSeconds: durationSeconds ?? null,
    submitted: results.length,
    completed: completed.length,
    failed: failed.length,
    unfinished: other.length,
    mockRequestCount,
    totalSeconds: round(totalMs / 1000, 3),
    instancesPerSecond: round(completed.length / (totalMs / 1000), 3),
    apiTasksPerSecond: round(completed.length / (totalMs / 1000), 3),
    latencyMs: {
      min: round(latencies[0] ?? 0, 1),
      p50: round(percentile(latencies, 0.5), 1),
      p95: round(percentile(latencies, 0.95), 1),
      p99: round(percentile(latencies, 0.99), 1),
      max: round(latencies[latencies.length - 1] ?? 0, 1)
    }
  };

  console.log(JSON.stringify(summary, null, 2));
  if (outputFile) {
    await writeFile(outputFile, `${JSON.stringify({ summary, results }, null, 2)}\n`, "utf8");
  }

  if (failed.length > 0 || other.length > 0) {
    process.exitCode = 1;
  }
} finally {
  await close(mockServer);
}

async function runForDuration(deployedKey, token, seconds, limit) {
  const results = [];
  const stopSubmittingAt = performance.now() + seconds * 1000;
  let nextIndex = 0;

  async function runOne() {
    while (performance.now() < stopSubmittingAt) {
      const current = nextIndex++;
      results[current] = await runInstance(deployedKey, token, current);
    }
  }

  await Promise.all(Array.from({ length: limit }, runOne));
  return results.filter(Boolean);
}

async function runInstance(deployedKey, token, index) {
  const submittedAt = performance.now();
  const instance = await request("POST", `/processes/${encodeURIComponent(deployedKey)}/start`, null, token);
  const id = instance.id;
  const deadline = performance.now() + timeoutSeconds * 1000;

  while (performance.now() < deadline) {
    const current = await request("GET", `/processes/instances/${id}`, null, token);
    if (current.status === "COMPLETED" || current.status === "FAILED" || current.status === "CANCELLED") {
      return {
        index,
        id,
        status: current.status,
        latencyMs: performance.now() - submittedAt,
        errorMessage: current.errorMessage
      };
    }
    await sleep(pollIntervalMs);
  }

  return {
    index,
    id,
    status: "TIMEOUT",
    latencyMs: performance.now() - submittedAt
  };
}

async function login() {
  const response = await request("POST", "/auth/login", { username, password }, null);
  return response.token;
}

async function request(method, path, body, token) {
  const headers = { accept: "application/json" };
  if (body !== null && body !== undefined) {
    headers["content-type"] = "application/json";
  }
  if (token) {
    headers.authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${backendUrl}${path}`, {
    method,
    headers,
    body: body === null || body === undefined ? undefined : JSON.stringify(body)
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(`${method} ${path} failed with ${response.status}: ${text}`);
  }

  return payload;
}

function buildProcessDefinition(id, url) {
  return {
    processId: id,
    key: id,
    processName: "Worker Throughput Benchmark",
    description: "Synthetic benchmark process: Start -> API task -> End.",
    nodes: [
      {
        id: "start",
        name: "Start",
        type: "StartEvent"
      },
      {
        id: "call-mock-api",
        name: "Call Mock API",
        type: "APITask",
        properties: {
          method: "GET",
          url
        }
      },
      {
        id: "end",
        name: "End",
        type: "EndEvent"
      }
    ],
    flows: [
      { from: "start", to: "call-mock-api" },
      { from: "call-mock-api", to: "end" }
    ]
  };
}

async function runPool(items, limit, worker) {
  const results = new Array(items.length);
  let next = 0;

  async function runOne() {
    while (next < items.length) {
      const current = next++;
      results[current] = await worker(items[current]);
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, runOne));
  return results;
}

function parseArgs(rawArgs) {
  const parsed = new Map();
  for (let i = 0; i < rawArgs.length; i += 1) {
    const item = rawArgs[i];
    if (!item.startsWith("--")) continue;

    const [key, inlineValue] = item.slice(2).split("=");
    if (inlineValue !== undefined) {
      parsed.set(key, inlineValue);
    } else if (rawArgs[i + 1] && !rawArgs[i + 1].startsWith("--")) {
      parsed.set(key, rawArgs[i + 1]);
      i += 1;
    } else {
      parsed.set(key, "true");
    }
  }
  return parsed;
}

function arg(name, fallback) {
  return args.get(name) ?? fallback;
}

function intArg(name, fallback) {
  const value = Number.parseInt(arg(name, String(fallback)), 10);
  if (Number.isNaN(value) || value <= 0) {
    throw new Error(`--${name} must be a positive integer`);
  }
  return value;
}

function optionalIntArg(name) {
  const raw = args.get(name);
  if (raw === undefined) return null;
  const value = Number.parseInt(raw, 10);
  if (Number.isNaN(value) || value <= 0) {
    throw new Error(`--${name} must be a positive integer`);
  }
  return value;
}

function boolArg(name, fallback) {
  const value = args.get(name);
  if (value === undefined) return fallback;
  return value === "true" || value === "1";
}

function percentile(values, p) {
  if (values.length === 0) return 0;
  const index = Math.min(values.length - 1, Math.ceil(values.length * p) - 1);
  return values[index];
}

function round(value, digits) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function listen(server, port) {
  return new Promise((resolve) => server.listen(port, "0.0.0.0", resolve));
}

function close(server) {
  return new Promise((resolve, reject) => {
    server.close((error) => {
      if (error) reject(error);
      else resolve();
    });
  });
}
