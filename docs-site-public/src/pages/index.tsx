import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

function HomepageHeader() {
  return (
    <header className={clsx(styles.heroBanner)}>
      <div className="container">
        <p className={styles.kicker}>Easy BPM Documentation</p>
        <Heading as="h1" className={styles.heroTitle}>
          Build business processes your developers can ship and operate.
        </Heading>
        <p className={styles.heroSubtitle}>
          Install the platform, model workflows, connect APIs, run human tasks, execute code tasks, and manage process operations through a documented REST API.
        </p>
        <div className={styles.buttons}>
          <Link
            className="button button--primary button--lg"
            to="/docs/getting-started/quick-start">
            Start with Quick Start
          </Link>
          <Link className="button button--secondary button--lg" to="/docs/api/authentication">
            View API Reference
          </Link>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title} Documentation`}
      description="Public developer documentation for Easy BPM customers.">
      <HomepageHeader />
      <main className={styles.main}>
        <section className="container">
          <div className={styles.cardGrid}>
            <Link className={styles.card} to="/docs/guides/create-process">
              <h2>Model processes</h2>
              <p>Create start events, user tasks, API tasks, gateways, message events, timers, and subprocesses.</p>
            </Link>
            <Link className={styles.card} to="/docs/guides/user-tasks">
              <h2>Run human work</h2>
              <p>Assign tasks, claim group work, render dynamic forms, complete work, and map variables forward.</p>
            </Link>
            <Link className={styles.card} to="/docs/guides/code-tasks">
              <h2>Execute code</h2>
              <p>Upload JVM JARs, discover classes and methods, configure Code Task nodes, and audit execution.</p>
            </Link>
            <Link className={styles.card} to="/docs/deployment/docker">
              <h2>Deploy the stack</h2>
              <p>Run backend, worker, PostgreSQL, RabbitMQ, modeler, admin, and portal with Docker or Helm.</p>
            </Link>
          </div>
        </section>
      </main>
    </Layout>
  );
}
