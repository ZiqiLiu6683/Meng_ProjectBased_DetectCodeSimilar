# AWS Evaluation Environment Runbook

Status: trial environment only
Default region: US East (Ohio), `us-east-2`
Security rule: never store AWS access keys, passwords, SSH private keys, or dataset credentials in this repository.

## 1. Purpose and rollout gates

The first EC2 instance validates the operating system, Java/Maven/Python toolchain,
repository build, versioned result schema, and checkpoint behavior. It is not a
paper-results machine.

Rollout order:

1. account security and cost budget;
2. small On-Demand trial instance;
3. repository bootstrap and targeted smoke tests;
4. manifest/config/cache implementation;
5. 1% preflight on a larger On-Demand instance;
6. frozen commit and configuration;
7. full experiment, optionally using interruption-tolerant Spot workers.

## 2. Account safety before EC2

Complete these in the AWS console before launching compute:

1. Enable MFA for the root user and do not use root for ordinary EC2 work.
2. Create a monthly AWS Cost Budget with email alerts.
3. Recommended initial budget alerts: 25%, 50%, 80%, and 100% actual cost, plus
   one forecast alert if AWS has enough usage history.
4. Use IAM/Identity Center for daily access.
5. Never create a long-lived access key merely to follow this runbook.

Budget alerts are delayed monitoring, not a guaranteed spending cap. Always stop
or terminate unused instances and inspect EBS/S3 resources that continue billing
after compute stops.

## 3. Trial EC2 configuration

Use the EC2 launch wizard with:

| Setting | Trial value |
| --- | --- |
| Name | `codesim-eval-trial` |
| Region | `us-east-2` (US East, Ohio) |
| AMI | Ubuntu Server 24.04 LTS, x86_64, official Canonical image |
| Purchase | On-Demand |
| Instance | `m7i-flex.large`; if unavailable, `m7i.large` or `m6i.large` |
| Root EBS | 80 GiB gp3, encrypted, delete on termination |
| Public IP | Not needed when Session Manager works |
| Tags | `Project=CodeSim`, `Environment=Trial`, `Owner=<your-name>` |

This trial size verifies functionality only. It must not be used for latency
claims or the full benchmark.

### Secure connection: preferred

Create an EC2 IAM role with the AWS-managed
`AmazonSSMManagedInstanceCore` policy and attach it as the instance profile. Use
EC2 **Connect -> Session Manager**. The security group then needs no inbound
rules.

If Session Manager is not yet available, use SSH only as a temporary fallback:

- create a new ED25519 key pair and store the private key outside the repository;
- allow TCP 22 from **My IP** only, never `0.0.0.0/0`;
- remove the inbound rule after Session Manager works.

## 4. Bootstrap

Paste the contents of `bootstrap-ubuntu.sh` into **Advanced details -> User data**
when launching, or run it once as root after connecting:

```bash
sudo bash code-sim/infra/aws/bootstrap-ubuntu.sh
```

Log out and reconnect after bootstrap so the `ubuntu` user receives Docker group
membership. Verify the host:

```bash
bash code-sim/infra/aws/verify-host.sh
```

## 5. Obtain the repository

For a public repository:

```bash
git clone https://github.com/ZiqiLiu6683/Meng_ProjectBased_DetectCodeSimilar.git
cd Meng_ProjectBased_DetectCodeSimilar
git rev-parse HEAD
git status --short
```

For a private repository, use a short-lived GitHub authentication method. Do not
put a token in user data, shell history, a Git URL, or the repository.

The AWS result must record the exact commit. Uncommitted local changes on the Mac
are not present after cloning; push an explicit experiment branch or transfer a
reviewed source archive before cloud validation.

## 6. Trial preflight

From the repository root:

```bash
bash code-sim/infra/aws/preflight.sh
```

Expected outputs:

- semantic Maven compilation succeeds;
- runner provenance tests succeed;
- existing WALA/T4 seam tests succeed;
- strict scorer tests succeed;
- no benchmark dataset or dynamic execution of external code is involved.

### Portable BCB plumbing smoke

After the preflight passes on a clean commit, build the runtime classpath once:

```bash
bash code-sim/infra/aws/run-bcb-smoke.sh
```

The script performs the validation, classpath build, and frozen run below. The
expanded commands are retained as an auditable reference.

```bash
mvn -f code-sim/pom.xml -Psemantic-analysis \
  dependency:build-classpath -Dmdep.outputFile=target/cp.txt
```

Run four deterministic shards through one JVM worker on the 8 GiB trial host.
This is a 140-pair infrastructure smoke test, not a paper result. Dynamic T4 is
disabled because BigCloneBench is the T1--T3 evidence source; T4 has separate
datasets and runs in the paper protocol.

```bash
python3 code-sim/scripts/experiments/run_shards.py \
  --manifest code-sim/results/bcb_smoke/manifest_v2.csv \
  --out code-sim/results/cloud-trial-bcb-smoke \
  --shards 4 \
  --workers 1 \
  --xmx 4g \
  --max-attempts 1 \
  --config-id bcb-smoke-t1t3-dynamic-off-v1 \
  --environment-id aws-us-east-2-m7i-flex-large-ubuntu-24.04-trial \
  --skip-dynamic
```

The runner validates every path, checksum, label, and reference range before
launching. It records the commit, dirty-worktree state, dataset/config IDs,
manifest hash, host metadata, shard logs, and one-line JSON attempt records.
Reusing an output directory with a different frozen configuration is rejected.

For the 32 GiB final-environment smoke (`m7i-flex.2xlarge`, 8 vCPU), use:

```bash
bash code-sim/infra/aws/run-bcb-smoke-32g.sh
```

This command refuses smaller hosts and runs eight 3 GiB JVM workers over eight
deterministic shards. Its output directory is separate from the 8 GiB trial, so
hardware environments are never mixed in one result set.

For the performance-first final candidate (`r8i.8xlarge`, 32 vCPU and 256 GiB),
use:

```bash
bash code-sim/infra/aws/run-bcb-smoke-r8i-8xlarge.sh
```

This command refuses smaller hosts and runs 32 deterministic shards with 32
workers and a 6 GiB heap per JVM. The total configured Java heap is 192 GiB,
leaving headroom for native memory, the operating system, and the harness.

## 7. Shutdown discipline

At the end of every session:

1. copy any needed logs/results off the instance;
2. stop the instance if the boot disk must be retained briefly;
3. terminate the trial instance when finished;
4. confirm that unneeded EBS volumes, snapshots, Elastic IPs, and S3 objects are
   not left behind;
5. review Cost Explorer and the Billing console.

Stopping an instance stops ordinary compute charges but retained EBS storage can
still incur charges. Termination is the preferred end state for this disposable
trial.

## 8. Later sizing decisions

Do not choose the final instance from vCPU count alone. The 1% preflight will
measure per-worker RSS, CPU utilization, local-disk I/O, WALA completion rate,
and cached/uncached throughput.

Initial candidates for the preflight are memory-balanced x86 instances. A
compute-optimized C7i worker is considered only if measured memory per concurrent
JVM fits its 2 GiB-per-vCPU ratio. The final paper records the exact instance
type, CPU model, memory, region, AMI ID, EBS configuration, JDK, and container
digest.
