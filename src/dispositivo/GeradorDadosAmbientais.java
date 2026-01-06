package dispositivo;

import utils.DadosAmbientais;
import java.util.Random;

public class GeradorDadosAmbientais {
    
    private static final Random random = new Random();
    
    private static final double CO2_MIN = 400;
    private static final double CO2_MAX = 2000;
    
    private static final double CO_MIN = 0;
    private static final double CO_MAX = 50;
    
    private static final double NO2_MIN = 0;
    private static final double NO2_MAX = 200;
    
    private static final double SO2_MIN = 0;
    private static final double SO2_MAX = 100;
    
    private static final double PM25_MIN = 0;
    private static final double PM25_MAX = 150;
    
    private static final double PM10_MIN = 0;
    private static final double PM10_MAX = 250;
    
    private static final double TEMP_MIN = 10;
    private static final double TEMP_MAX = 40;
    
    private static final double UMIDADE_MIN = 30;
    private static final double UMIDADE_MAX = 90;
    
    private static final double RUIDO_MIN = 40;
    private static final double RUIDO_MAX = 100;
    
    private static final double UV_MIN = 0;
    private static final double UV_MAX = 11;
    
    public static DadosAmbientais gerar(String dispositivoId, String localizacao) {
        DadosAmbientais dados = new DadosAmbientais();
        dados.setDispositivoId(dispositivoId);
        dados.setLocalizacao(localizacao);
        dados.setCo2(valorAleatorio(CO2_MIN, CO2_MAX));
        dados.setCo(valorAleatorio(CO_MIN, CO_MAX));
        dados.setNo2(valorAleatorio(NO2_MIN, NO2_MAX));
        dados.setSo2(valorAleatorio(SO2_MIN, SO2_MAX));
        dados.setPm25(valorAleatorio(PM25_MIN, PM25_MAX));
        dados.setPm10(valorAleatorio(PM10_MIN, PM10_MAX));
        dados.setTemperatura(valorAleatorio(TEMP_MIN, TEMP_MAX));
        dados.setUmidade(valorAleatorio(UMIDADE_MIN, UMIDADE_MAX));
        dados.setRuido(valorAleatorio(RUIDO_MIN, RUIDO_MAX));
        dados.setUv(valorAleatorio(UV_MIN, UV_MAX));
        return dados;
    }
    
    private static double valorAleatorio(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }
}

