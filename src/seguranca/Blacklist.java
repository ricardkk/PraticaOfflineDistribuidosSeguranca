package seguranca;

import utils.Cores;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class Blacklist {
    
    private final Set<String> ipsBlacklist;
    private final Set<String> idsBlacklist;
    private final Map<String, LocalDateTime> historicoBloqueioss;
    
    public Blacklist() {
        this.ipsBlacklist = ConcurrentHashMap.newKeySet();
        this.idsBlacklist = ConcurrentHashMap.newKeySet();
        this.historicoBloqueioss = new ConcurrentHashMap<>();
    }
    
    public void bloquearIP(String ip) {
        ipsBlacklist.add(ip);
        historicoBloqueioss.put("IP:" + ip, LocalDateTime.now());
        log(Cores.vermelho("IP BLOQUEADO: " + ip));
    }
    
    public void bloquearID(String id) {
        idsBlacklist.add(id);
        historicoBloqueioss.put("ID:" + id, LocalDateTime.now());
        log(Cores.vermelho("ID BLOQUEADO: " + id));
    }
    
    public void desbloquearIP(String ip) {
        ipsBlacklist.remove(ip);
        log(Cores.verde("IP DESBLOQUEADO: " + ip));
    }
    
    public void desbloquearID(String id) {
        idsBlacklist.remove(id);
        log(Cores.verde("ID DESBLOQUEADO: " + id));
    }
    
    public boolean isIPBloqueado(String ip) {
        return ipsBlacklist.contains(ip);
    }
    
    public boolean isIDBloqueado(String id) {
        return idsBlacklist.contains(id);
    }
    
    public boolean isBloqueado(String ip, String id) {
        return isIPBloqueado(ip) || isIDBloqueado(id);
    }
    
    public Set<String> getIPsBloqueados() {
        return Set.copyOf(ipsBlacklist);
    }
    
    public Set<String> getIDsBloqueados() {
        return Set.copyOf(idsBlacklist);
    }
    
    public int getTotalBloqueios() {
        return ipsBlacklist.size() + idsBlacklist.size();
    }
    
    public void exibirStatus() {
        log("=".repeat(50));
        log("STATUS DA BLACKLIST");
        log("=".repeat(50));
        log("IPs bloqueados: " + ipsBlacklist.size());
        for (String ip : ipsBlacklist) {
            LocalDateTime quando = historicoBloqueioss.get("IP:" + ip);
            log("  - " + ip + " (desde " + formatarData(quando) + ")");
        }
        log("IDs bloqueados: " + idsBlacklist.size());
        for (String id : idsBlacklist) {
            LocalDateTime quando = historicoBloqueioss.get("ID:" + id);
            log("  - " + id + " (desde " + formatarData(quando) + ")");
        }
        log("=".repeat(50));
    }
    
    private String formatarData(LocalDateTime dt) {
        if (dt == null) return "desconhecido";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
    
    private void log(String mensagem) {
        System.out.println("[BLACKLIST] " + mensagem);
    }
}
