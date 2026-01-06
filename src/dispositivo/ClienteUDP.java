package dispositivo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ClienteUDP {
    
    private String host;
    private int porta;
    private DatagramSocket socket;
    
    public ClienteUDP(String host, int porta) throws Exception {
        this.host = host;
        this.porta = porta;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(5000);
    }
    
    public void enviar(byte[] dados) throws Exception {
        InetAddress endereco = InetAddress.getByName(host);
        DatagramPacket pacote = new DatagramPacket(dados, dados.length, endereco, porta);
        socket.send(pacote);
    }
    
    public byte[] receberResposta(int tamanhoBuffer) throws Exception {
        byte[] buffer = new byte[tamanhoBuffer];
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
        socket.receive(pacote);
        byte[] dados = new byte[pacote.getLength()];
        System.arraycopy(buffer, 0, dados, 0, pacote.getLength());
        return dados;
    }
    
    public void fechar() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}

