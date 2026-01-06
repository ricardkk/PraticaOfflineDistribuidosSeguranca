package utils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DadosAmbientais implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String dispositivoId;
    private LocalDateTime timestamp;
    private double co2;
    private double co;
    private double no2;
    private double so2;
    private double pm25;
    private double pm10;
    private double temperatura;
    private double umidade;
    private double ruido;
    private double uv;
    private String localizacao;
    
    public DadosAmbientais() {
        this.timestamp = LocalDateTime.now();
    }
    
    public String getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(String dispositivoId) { this.dispositivoId = dispositivoId; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public double getCo2() { return co2; }
    public void setCo2(double co2) { this.co2 = co2; }
    
    public double getCo() { return co; }
    public void setCo(double co) { this.co = co; }
    
    public double getNo2() { return no2; }
    public void setNo2(double no2) { this.no2 = no2; }
    
    public double getSo2() { return so2; }
    public void setSo2(double so2) { this.so2 = so2; }
    
    public double getPm25() { return pm25; }
    public void setPm25(double pm25) { this.pm25 = pm25; }
    
    public double getPm10() { return pm10; }
    public void setPm10(double pm10) { this.pm10 = pm10; }
    
    public double getTemperatura() { return temperatura; }
    public void setTemperatura(double temperatura) { this.temperatura = temperatura; }
    
    public double getUmidade() { return umidade; }
    public void setUmidade(double umidade) { this.umidade = umidade; }
    
    public double getRuido() { return ruido; }
    public void setRuido(double ruido) { this.ruido = ruido; }
    
    public double getUv() { return uv; }
    public void setUv(double uv) { this.uv = uv; }
    
    public String getLocalizacao() { return localizacao; }
    public void setLocalizacao(String localizacao) { this.localizacao = localizacao; }
    
    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format(
            "[%s] %s @ %s | CO2:%.1fppm CO:%.1fppm NO2:%.1fppb SO2:%.1fppb | " +
            "PM2.5:%.1fμg/m³ PM10:%.1fμg/m³ | Temp:%.1f°C Umid:%.1f%% | " +
            "Ruído:%.1fdB UV:%.1f",
            timestamp.format(formatter), dispositivoId, localizacao,
            co2, co, no2, so2, pm25, pm10, temperatura, umidade, ruido, uv
        );
    }
    
    public String toCSV() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return String.format("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f",
            timestamp.format(formatter), dispositivoId, localizacao,
            co2, co, no2, so2, pm25, pm10, temperatura, umidade, ruido, uv
        );
    }
    
    public static String getCSVHeader() {
        return "timestamp,dispositivo_id,localizacao,co2,co,no2,so2,pm25,pm10,temperatura,umidade,ruido,uv";
    }
}

