# USB Tethering Setup

This guide covers how to connect the Android app to the Raspberry Pi via USB tethering, including auto-enabling tethering and automatic Pi discovery.

## 1. Auto-enable USB tethering (no manual toggle)

By default, Android requires you to manually enable USB tethering each time you plug in. You can change this so tethering turns on automatically:

### Method: Developer Options (recommended)

1. **Enable Developer Options**  
   Settings → About Phone → tap **Build Number** 7 times.

2. **Set default USB mode**  
   Settings → Developer Options → find **Default USB configuration** (or "Select USB Configuration").

3. **Choose USB tethering**  
   Set it to **USB tethering** or **RNDIS**.

4. **Result**  
   When you plug the phone into the Pi via USB, tethering will turn on automatically. You may need to unlock the phone once.

This works on many devices (Android 9+). If your device does not offer this option, see alternatives below.

### Alternatives if Developer Options doesn't have it

- **Tasker / Automate** – Create a profile: trigger = "USB plugged", action = enable USB tethering.
- **Quick Settings** – Add USB tethering to the quick settings panel for a one-tap toggle.

---

## 2. Automatic Pi discovery (no manual IP entry)

The app can discover the Pi on the USB tether subnet so you don’t need to enter its IP manually.

### How it works

1. Set **Pi host** to `auto` in Settings (this is the default).
2. When you tap **Connect**, the app:
   - Reads the default gateway (phone’s IP on the USB network, e.g. `192.168.171.1`)
   - Probes `https://IP:8000/health` for IPs in that subnet
   - Connects to the first host that responds
3. The discovered IP is saved for next time.

### Requirements

- USB tethering must be on (see section 1).
- The Pi must be running the HUD server (`hud.service` or `start.sh`).
- The Pi gets its IP via DHCP from the phone (configured in `usb0.nmconnection`).

**Important:** The HUD does **not** need internet or home Wi‑Fi. The phone must only reach the Pi on the **USB tether** network. If **Wi‑Fi is still on**, Android may treat the Wi‑Fi network as “active,” so **auto-discovery probes the wrong subnet** (your home router, not the phone). **In the car, turn Wi‑Fi off** on the phone so the active network is USB tethering (or set the Pi’s **manual IP** from `ip addr show usb0` on the Pi).

- **Tip:** If discovery still fails, use a **manual IP** in Settings (see below).

### Manual IP fallback

If discovery fails (e.g. wrong network, firewall), you can set the Pi IP manually in Settings. On the Pi, run:

```bash
ip addr show usb0
```

Use the `inet` address (e.g. `192.168.171.140`) in the app’s Pi host field.

---

## 3. Pi-side configuration

The Pi is already set up for USB tethering:

- **NetworkManager** (`usb0.nmconnection`): `method=auto` so the Pi gets an IP from the phone via DHCP.
- **Avahi**: Hostname `carhud` so the Pi can be reached as `carhud.local` (if mDNS works on your Android).
- **Hostname**: `hostnamectl set-hostname carhud` (done by `setup.sh`).

If `carhud.local` does not resolve on your phone, use `auto` or the Pi’s IP from `ip addr show usb0`.
