# Deploy host: Oracle Cloud Always Free (D-15)

How the live GridReserve is set up, step by step. Everything here is free. Steps marked **you** need your
accounts or your hands; the rest is automated by `.github/workflows/deploy.yml`. Console labels change from
time to time; if one differs, look for the nearest match.

Never paste a secret (private key, `.env` value, Google token) into a chat, an issue or a commit. Public keys
are fine.

## 0. What you end up with

- One Ubuntu VM (Ampere A1, arm64) running the whole stack with Docker Compose from `/opt/gridreserve`.
- `https://<ip-with-dashes>.sslip.io` serves the app; only ports 80 and 443 are open.
- Every merge to `main` that passes CI is released automatically and smoke-tested.
- A backup every night at 02:30 UTC, encrypted, copied to your Google Drive.

## 1. Oracle account (you)

1. Sign up at oracle.com/cloud/free. Oracle asks for a credit or debit card **to verify identity only**;
   Always Free services cost nothing. **Do not upgrade to Pay As You Go**: that is where charges can start.
2. Choose the home region carefully (it can't be changed). Ampere capacity differs by region; one near you is
   fine.
3. Optional safety net: Billing → Budgets → a budget of 1 (your currency) with an email alert.

## 2. The VM (you)

First the network, so the instance form can give the VM a public IP (with "Create new public subnet" in that
form the public-IP switch stays disabled, because the subnet doesn't exist yet): Networking → Virtual cloud
networks → **Start VCN Wizard** → **Create VCN with Internet Connectivity**, name `gridreserve-vcn`, defaults,
Create. That makes a public subnet, an internet gateway, the route and a security list with SSH open.

Then Compute → Instances → Create instance:

| Setting | Value |
|---|---|
| Image | Canonical Ubuntu 24.04 (Minimal) **aarch64**; the warning about the current shape is expected, the shape changes below |
| Shape | VM.Standard.A1.Flex (Ampere), the most OCPUs and memory the Always Free limit allows (at least 2 OCPU / 8 GB) |
| Networking | **Select existing**: `gridreserve-vcn` and its **public** subnet; **Automatically assign public IPv4 address**: on |
| SSH keys | **Paste public keys**: `pbcopy < ~/.ssh/id_ed25519.pub`, then paste (create the pair with `ssh-keygen -t ed25519` if you have none; never upload the file without `.pub`) |
| Storage | defaults: boot volume 46.6 GB (Always Free covers up to 200 GB in total), in-transit encryption on, no block volumes |

"Out of capacity for shape VM.Standard.A1.Flex" is common: try another availability domain (Basic information
→ Placement; many regions have only AD-1), leave the fault domain to Oracle, use 1 OCPU / 6 GB (enough; resize
later), or try again at another time of day. Don't upgrade to Pay As You Go to get past it. Meanwhile the
laptop tunnel (`scripts/demo-tunnel.sh`) is the live demo.

Write down the public IP, e.g. `203.0.113.5`. Your site name is then `203-0-113-5.sslip.io`.
sslip.io answers every such name with the IP inside it, so there's no DNS to set up.

## 3. Open ports 80 and 443 (you)

Two firewalls must both allow them:

1. **Oracle's network:** Instance → subnet → Security List → Add Ingress Rules: source `0.0.0.0/0`, TCP,
   destination ports `80` and then `443`. (22 is open already.)
2. **Ubuntu's own iptables** (Oracle's Ubuntu images reject everything except SSH):
   ```bash
   ssh ubuntu@203.0.113.5
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save     # Minimal image: sudo apt-get install -y iptables-persistent first
   ```

Do **not** open 5432, 8080, 8761 or any other port. The stack publishes nothing else to the internet, and
the smoke test checks that from outside.

## 4. Docker and the folder (you, on the VM)

```bash
# Docker Engine + Compose plugin from Docker's own repository (docs.docker.com/engine/install/ubuntu)
sudo apt-get update && sudo apt-get install -y ca-certificates curl git python3
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu          # then log out and in again
sudo systemctl enable docker            # start Docker (and so the stack) after a reboot

sudo mkdir -p /opt/gridreserve && sudo chown ubuntu: /opt/gridreserve
git clone https://github.com/chhavinath-bit/GridReserve.git ~/GridReserve
```

Being in the `docker` group is as powerful as root; on a single-purpose server that's the usual trade-off.

## 5. Secrets: the host's `.env` (you, on the VM)

```bash
~/GridReserve/scripts/prod/init-env.sh 203-0-113-5.sslip.io
```

It writes `/opt/gridreserve/.env` (only you can read it) with generated database passwords, the service-client
secret, the backup encryption password and an RS256 key pair, and prints two things:

1. The **public** key. Add it to `gridreserve-config/application-prod.yml` as `prod-2026-11` (replacing `{}`), in
   the M1.10 config PR (or send it to Claude, since it's public). The private key never leaves the VM.
2. A reminder to copy `RCLONE_CRYPT_PASSWORD` and `RCLONE_CRYPT_SALT` from `.env` to a password manager.
   **Without them no backup can be decrypted**, and losing the VM loses `.env` too.

## 6. Google Drive for backups (you)

The backup container needs a Google Drive token with the `drive.file` scope: rclone can then see only the
files it creates itself, not the rest of your Drive.

On your **Mac** (with a browser):

```bash
brew install rclone
rclone config create gdrive drive scope=drive.file     # opens the browser: sign in, allow
rclone config show gdrive | grep '^token'              # one line: token = {"access_token":...}
```

Copy the JSON after `token = ` into `RCLONE_GDRIVE_TOKEN=` in `/opt/gridreserve/.env` on the VM
(`nano /opt/gridreserve/.env`), on one line. Then remove it from your Mac: `rclone config delete gdrive`.

The backups appear in Drive under `gridreserve-backups/` with scrambled names: rclone's crypt layer encrypts
names and contents before upload.

## 7. Let GitHub deploy (you)

1. A key pair used only for deploys, on your Mac:
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/gridreserve-deploy -N "" -C gridreserve-deploy
   ssh-copy-id -i ~/.ssh/gridreserve-deploy.pub ubuntu@203.0.113.5
   ssh-keyscan -t ed25519 203.0.113.5          # the host's key line, for DEPLOY_KNOWN_HOSTS
   ```
2. GitHub → GridReserve → Settings → Secrets and variables → Actions:

   | Kind | Name | Value |
   |---|---|---|
   | Variable | `DEPLOY_HOST` | `203.0.113.5` |
   | Variable | `DEPLOY_USER` | `ubuntu` |
   | Variable | `DEPLOY_SITE` | `203-0-113-5.sslip.io` |
   | Secret | `DEPLOY_SSH_KEY` | the contents of `~/.ssh/gridreserve-deploy` (the private file) |
   | Secret | `DEPLOY_KNOWN_HOSTS` | the line `ssh-keyscan` printed |
   | Secret | `SMOKE_PASSWORD` | any long random string (`openssl rand -hex 16`) |

3. After the first CI run on `main` has pushed the images: GitHub → your profile → Packages → each
   `gridreserve-*` package → Package settings → Change visibility → **Public**. The VM pulls them without a
   login. They contain no secrets: those are only in the VM's `.env`, as checked with `docker history` in M1.10.

## 8. First release

Merge the config PR (with the prod public key), then the app PR. CI builds and pushes the images, and Deploy
runs. If it ran before the packages were public, rerun it: Actions → Deploy → Run workflow (empty tag =
latest `main`).

Then check it yourself:

- `https://203-0-113-5.sslip.io`: log in, book, reload (still logged in), My bookings.
- On the VM: `cd /opt/gridreserve && docker compose ps` shows every service healthy, `backup` included (its
  first backup runs at start).

## 9. Operating it

| Task | How |
|---|---|
| Status | `cd /opt/gridreserve && docker compose ps` |
| Logs | `docker compose logs --tail=100 booking-service` |
| Roll back | Actions → Deploy → Run workflow with an older commit SHA (`PREVIOUS_IMAGE_TAG` in `.env` is the last one) |
| Config change without restart | merge the config PR, then on the VM: `GRIDRESERVE_ADMIN_EMAIL=… GRIDRESERVE_ADMIN_PASSWORD=… COMPOSE_DIR=/opt/gridreserve GATEWAY_URL=http://localhost:8080 ./refresh-config.sh` |
| Backup now | `docker compose run --rm backup backup.sh` |
| Restore drill (safe: a throwaway database) | `cd /opt/gridreserve && ~/GridReserve/scripts/prod/restore-drill.sh` |
| Real restore | `docker compose stop identity-service booking-service`, then `docker compose run --rm backup restore.sh <name>`, then `docker compose start identity-service booking-service` |
| Eureka dashboard | from your Mac: `ssh -L 8761:localhost:8761 ubuntu@203.0.113.5`, then http://localhost:8761 |

Checks for PLAT-07, done once after the first release:

- `sudo reboot`: after a few minutes the site is back, and your bookings are still there.
- `docker compose stop config-server && docker compose restart identity-service`: identity fails fast and
  restarts again and again; `docker compose start config-server`: identity becomes healthy by itself.

## 10. If the VM is lost (reclaimed, deleted)

Oracle may reclaim Always Free VMs that stay idle for a week. Rebuilding:

1. Steps 2–4 for a new VM (new IP, so a new sslip.io name: update `DEPLOY_HOST`, `DEPLOY_SITE` and
   `DEPLOY_KNOWN_HOSTS`).
2. Step 5, then **replace** the two generated `RCLONE_CRYPT_*` values in `.env` with the saved ones, and put
   the new public key in the config repo. Users must log in again (new signing key).
3. Step 6 (Google token), then run Deploy once.
4. Restore the last backup: the "Real restore" row above. The newest backup is fetched from Drive and
   decrypted.

While the VM is down, `scripts/demo-tunnel.sh` on your laptop gives a temporary public address for interviews.
