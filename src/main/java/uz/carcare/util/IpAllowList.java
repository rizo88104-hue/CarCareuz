package uz.carcare.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * IP ruxsat ro'yxati: "1.2.3.4, 10.0.0.0/8, 2001:db8::/32" ko'rinishidagi matndan tuziladi.
 * Ro'yxat bo'sh bo'lsa — hamma IP ga ruxsat (cheklov o'chiq).
 */
public final class IpAllowList {

    private record Rule(byte[] network, int prefixBits) {
        boolean matches(byte[] addr) {
            if (addr.length != network.length) return false;
            int fullBytes = prefixBits / 8, restBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) if (addr[i] != network[i]) return false;
            if (restBits == 0) return true;
            int mask = 0xFF << (8 - restBits);
            return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    private final List<String> invalid = new ArrayList<>();

    public IpAllowList(String spec) {
        if (spec == null) return;
        for (String part : spec.split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            try {
                String ipPart = part;
                int prefix = -1;
                int slash = part.indexOf('/');
                if (slash >= 0) {
                    ipPart = part.substring(0, slash);
                    prefix = Integer.parseInt(part.substring(slash + 1));
                }
                byte[] bytes = InetAddress.getByName(ipPart).getAddress();
                int max = bytes.length * 8;
                if (prefix < 0) prefix = max;
                if (prefix > max) throw new IllegalArgumentException("prefix");
                rules.add(new Rule(bytes, prefix));
            } catch (UnknownHostException | IllegalArgumentException e) {
                invalid.add(part);
            }
        }
    }

    /** Cheklov yoqilganmi (kamida bitta to'g'ri qoida bor). */
    public boolean enabled() {
        return !rules.isEmpty();
    }

    /** Noto'g'ri yozilgan qoidalar (ishga tushganda logga chiqarish uchun). */
    public List<String> invalidEntries() {
        return invalid;
    }

    public boolean allows(String ip) {
        if (rules.isEmpty()) return true;
        if (ip == null || ip.isBlank()) return false;
        try {
            byte[] addr = InetAddress.getByName(ip).getAddress();
            // IPv4 ni IPv6 ko'rinishida (::ffff:1.2.3.4) kelsa ham tekshirish
            for (Rule r : rules) if (r.matches(addr)) return true;
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
