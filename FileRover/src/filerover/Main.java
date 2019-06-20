package filerover;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.DocumentEvent.EventType;
import javax.swing.text.AbstractDocument.Content;

import com.sun.glass.events.WindowEvent;
import com.sun.glass.ui.Timer;
import com.sun.xml.internal.ws.util.StringUtils;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.InputMethodTextRun;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class Main extends Application {

	private Stage stage;
	private File arquivo;
	private File ultimoDiretorio = null;

	private TextArea textArea;

	private int posicaoAtual = 0;

	private static String[] commandArgs;
	private long bloco = 0;
	private long qtdBlocos = 0;
	private Text txtBloco = new Text("0");
	private Text txtQtdBlocos = new Text("0");

	private int BLOCKSIZE = 32 * 1024;
	private double KILOBYTE = 1024;
	private double MEGABYTE = Math.pow(1024, 2);
	private double GIGABYTE = Math.pow(1024, 3);
	private double TERABYTE = Math.pow(1024, 4);

	private Text txtLinha = new Text("0");
	private Text txtColuna = new Text("0");
	private Text txtTamanho = new Text("0");
	private Text txtTotLinhas = new Text("0");
	private List<Integer> listaLinhas = new ArrayList<>();

	private int modo = 0;
	private int tipoArquivo = 0;
	private int MODONORMAL = 0;
	private int MODOHEXA = 1;
	private int TIPOARQUIVOTEXTO = 0;
	private int TIPOARQUIVOBINARIO = 1;

	protected boolean blnStop = false;

	protected class GlobalListener implements Runnable {

		private int process = 0;

		/**
		 * @return the process
		 */
		public int getProcess() {
			return process;
		}

		/**
		 * @param process
		 *            the process to set
		 */
		public void setProcess(int process) {
			this.process = process;
		}

		@Override
		public void run() {
			switch (process) {
			case 0:
				glRun(); // Inicia thread de verificação de posição.
				break;
			case 1:
				clRun(); // Conta linhas totais do arquivo em background.
			}

		}
	}

	@Override
	public void start(Stage primaryStage) {
		try {

			primaryStage.setTitle("File Rover");

			/*
			 * Criação da janela final
			 */
			stage = primaryStage;
			Scene scene = new Scene(telaPrincipal(), 800, 600);
			primaryStage.setScene(scene);

			ChangeListener<Number> stageSizeListener = (observable, oldValue, newValue) -> System.out
					.println("Height: " + stage.getHeight() + " Width: " + stage.getWidth());

			primaryStage.heightProperty().addListener(stageSizeListener);

			primaryStage.setOnCloseRequest(e -> {
				blnStop = true;
			});

			primaryStage.show();

			if (commandArgs.length > 0) {
				System.out.println(commandArgs[0]);
				abrirArquivo(commandArgs[0]);
			}

			GlobalListener g = new GlobalListener();
			Thread gl = new Thread(g);
			gl.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		commandArgs = args;
		launch(args);
	}

	/**
	 * Montagem da tela.
	 * 
	 * @return
	 */
	private VBox telaPrincipal() {

		VBox vBox = new VBox(montarMenu(), montarBotoes(), montarTextArea(), montarRodape());

		vBox.setFillWidth(true);
		vBox.setPadding(new Insets(10));

		return vBox;

	}

	private MenuBar montarMenu() {

		// Configura opções do menu.
		MenuBar menuBar = new MenuBar();
		Menu mnuArquivo = new Menu("Arquivo");
		Menu mnuOpcoes = new Menu("Opções");
		menuBar.getMenus().add(mnuArquivo);
		menuBar.getMenus().add(mnuOpcoes);
		MenuItem mnuAbrir = new MenuItem("Abrir");
		MenuItem mnuFechar = new MenuItem("Fechar");
		MenuItem mnuModoNormal = new MenuItem("Modo Normal");
		MenuItem mnuModoHexa = new MenuItem("Modo Hexadecimal");

		mnuArquivo.getItems().add(mnuAbrir);
		mnuArquivo.getItems().add(mnuFechar);
		mnuOpcoes.getItems().add(mnuModoNormal);
		mnuOpcoes.getItems().add(mnuModoHexa);

		// Configura métodos de ação dos itens do menu.
		mnuAbrir.setOnAction(e -> {
			mnuAbrir_Click();
		});
		mnuFechar.setOnAction(e -> {
			mnuFechar_Click();
		});

		mnuModoNormal.setOnAction(e -> {
			mnuModoNormal_Click();
		});
		mnuModoHexa.setOnAction(e -> {
			mnuModoHexa_Click();
		});

		return menuBar;

	}

	private HBox montarBotoes() {

		Button btnPrevBloco = new Button("<");
		Button btnProxBloco = new Button(">");

		HBox buttonsBar = new HBox(btnPrevBloco, new Label("   Bloco: "), txtBloco, new Label(" / "), txtQtdBlocos,
				new Label("   "), btnProxBloco);

		btnProxBloco.setOnAction(e -> {
			lerBloco(1);
		});

		btnPrevBloco.setOnAction(e -> {
			lerBloco(-1);
		});

		return buttonsBar;
	}

	private TextArea montarTextArea() {

		// Adiciona área de texto do conteúdo.

		TextArea textArea = new TextArea();

		textArea.setScaleShape(true);
		textArea.setText("* Nenhum arquivo carregado *");
		textArea.setEditable(true);
		textArea.setPrefSize(1920, 1080);
		textArea.setFont(new Font("Consolas", 14));

		textArea.setOnKeyPressed(e -> {

			// Desabilita somente combinações que modificam o texto.
			if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE
					|| e.isControlDown() && (e.getCode() == KeyCode.V)) {
				e.consume();
			}

			// Ações com Control.
			if (e.isControlDown()) {

				// Salto de bloco com Control Pagedown / Pageup
				if (e.getCode() == KeyCode.PAGE_DOWN) {
					lerBloco(1);
				} else if (e.getCode() == KeyCode.PAGE_UP) {
					lerBloco(-1);
				} else if (e.getCode() == KeyCode.HOME) {
					// Control HOME vai para o início do arquivo.
					bloco = 1;
					lerBloco(0);
				} else if (e.getCode() == KeyCode.END) {
					// Control END vai para o fim do arquivo.
					bloco = qtdBlocos;
					lerBloco(0);
				} else if (e.getCode() == KeyCode.H) {
					// Control H alterna modo Hexa e Normal.
					if (modo == MODONORMAL) {
						modo = MODOHEXA;
					} else {
						modo = MODONORMAL;
					}
					lerBloco(0);
				}

			}

		});

		textArea.setOnKeyTyped(e -> {
			// Bloqueia digitação de caracteres, readonly com cursor.
			e.consume();
		});

		ContextMenu cm = new ContextMenu(); // Bloqueia menu de contexto padrão.
		textArea.setContextMenu(cm);

		this.textArea = textArea;

		return textArea;

	}

	private HBox montarRodape() {

		HBox hbox = new HBox(new Label("Linha: "), txtLinha, new Label("   Coluna: "), txtColuna,
				new Label("   Tamanho: "), txtTamanho, new Label("   Qtd. Linhas: "), txtTotLinhas);

		return hbox;

	}

	private void mnuAbrir_Click() {
		FileChooser fileChooser = new FileChooser();

		fileChooser.setInitialDirectory(ultimoDiretorio);
		File arqAbrir = fileChooser.showOpenDialog(stage);
		if (arqAbrir == null)
			return;

		abrirArquivo(arqAbrir.getAbsolutePath());

		ultimoDiretorio = arqAbrir.getParentFile();

	}

	private void abrirArquivo(String caminhoArquivo) {

		arquivo = new File(caminhoArquivo);

		stage.setTitle("File Rover - " + arquivo.getName());
		bloco = 1;
		qtdBlocos = arquivo.length() / BLOCKSIZE;
		if (qtdBlocos * BLOCKSIZE < arquivo.length()) {
			qtdBlocos++;
		}

		double tamAbrev = arquivo.length();
		String unidade = "";

		// Formata tamanho abreviado.
		if (tamAbrev > TERABYTE) {
			tamAbrev = tamAbrev / TERABYTE;
			unidade = " TB";
		} else if (tamAbrev > GIGABYTE) {
			tamAbrev = tamAbrev / GIGABYTE;
			unidade = " GB";
		} else if (tamAbrev > MEGABYTE) {
			tamAbrev = tamAbrev / MEGABYTE;
			unidade = " MB";
		} else if (tamAbrev > KILOBYTE) {
			tamAbrev = tamAbrev / KILOBYTE;
			unidade = " KB";
		}

		String txtTamAbrev = " (" + String.format("%.02f", tamAbrev) + unidade + ")";

		txtTamanho.setText(String.valueOf(arquivo.length()) + txtTamAbrev);

		verificarTipoArquivo();
		lerBloco(0);
		
		// Se for arquivo texto, dispara thread de contagem de linhas totais em background.
		if (tipoArquivo == TIPOARQUIVOTEXTO) {
			GlobalListener g = new GlobalListener();
			g.setProcess(1); // Indica processo de contagem de linhas.
			Thread gl = new Thread(g);
			gl.start();
			
		}

	}

	/**
	 * Verifica o conteúdo do arquivo para identificar se é um tipo Texto ou
	 * binário.
	 */
	private void verificarTipoArquivo() {

		// Carrega primeiro bloco para análise.

		if (arquivo == null) {
			return;
		}

		try {
			FileInputStream f = new FileInputStream(arquivo);
			f.skip((bloco - 1) * BLOCKSIZE);
			byte[] buffer = new byte[BLOCKSIZE];
			int lidos = f.read(buffer, 0, BLOCKSIZE);
			f.close();

			if (lidos == 0) {
				return;
			}

			modo = MODONORMAL;
			tipoArquivo = TIPOARQUIVOTEXTO;

			for (int i = 0; i < lidos; i++) {

				int b = Byte.toUnsignedInt(buffer[i]);

				if (b < 32 && b != 13 && b != 10 && b != 9) {
					modo = MODOHEXA;
					tipoArquivo = TIPOARQUIVOBINARIO;
					break;
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Le bloco do arquivo na posição configurada.
	 */
	private void lerBloco(int deslocar) {

		if (arquivo == null) {
			return;
		}

		if (deslocar > 0 && bloco < qtdBlocos) {

			bloco++;
		} else if (deslocar < 0 && bloco > 1) {
			bloco--;
		}

		try {

			FileInputStream f = new FileInputStream(arquivo);
			f.skip((bloco - 1) * BLOCKSIZE);
			byte[] buffer = new byte[BLOCKSIZE];
			int lidos = f.read(buffer, 0, BLOCKSIZE);
			f.close();

			if (modo == MODONORMAL) {

				textArea.setText(new String(buffer, "Windows-1252"));
				contarLinhasTexto();

			} else {// HEXA

				StringBuilder hexValues = new StringBuilder("");
				StringBuilder txtValues = new StringBuilder("");

				int hexBytesLine = 0;

				for (int i = 0; i <= (lidos - 1); i++) {

					// Representação Hexa
					if (hexBytesLine == 16) {
						hexValues.append(" ").append(txtValues.toString()).append("\r\n");
						txtValues = new StringBuilder("");
						hexBytesLine = 0;

					}

					String hexByte = Integer.toHexString(buffer[i]).toUpperCase();
					if (buffer[i] < 0x10) {
						hexByte = "0" + hexByte;
						if (hexByte.length() > 2) {
							hexByte = hexByte.substring(hexByte.length() - 2, hexByte.length());
						}
					}

					hexValues.append(hexByte + " ");

					// Representação textual.
					int b = Byte.toUnsignedInt(buffer[i]);
					String c;

					if (b >= 32 && b != 129 && b != 141 && b != 143 && b != 144 && b != 157) {

						byte[] caracter = { 0 };
						caracter[0] = buffer[i];
						c = new String(caracter, "Windows-1252");

					} else {
						c = ".";
					}

					txtValues.append(c);

					// última linha
					if (i == (lidos - 1)) {

						int espacos = (14 - hexBytesLine);

						for (int i2 = 0; i2 <= espacos; i2++) {
							hexValues.append("   ");
						}

						hexValues.append(" ").append(txtValues.toString());
						txtValues = new StringBuilder("");
					}

					hexBytesLine++;
				}

				textArea.setText(hexValues.toString());

			}

			txtBloco.setText(String.valueOf(bloco));
			txtQtdBlocos.setText(String.valueOf(qtdBlocos));

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void mnuFechar_Click() {
		arquivo = null;
		textArea.setText("");
		stage.setTitle("File Rover");
		txtBloco.setText(String.valueOf(0));
		txtQtdBlocos.setText(String.valueOf(0));
	}

	private void mnuModoNormal_Click() {
		modo = MODONORMAL;
		if (arquivo != null) {
			lerBloco(0);
		}
	}

	private void mnuModoHexa_Click() {
		modo = MODOHEXA;
		if (arquivo != null) {
			lerBloco(0);
		}
	}

	private int contarLinhas() {
		// Conta quantas linhas existem no arquivo.

		try {
			FileInputStream f = new FileInputStream(arquivo);
			byte[] buffer = new byte[BLOCKSIZE];

			int lidos = 0;
			int linhas = 0;
			int i;

			lidos = f.read(buffer, 0, BLOCKSIZE);
			while (lidos > 0 && !blnStop) {
				for (i = 0; i < lidos; i++) {
					if (buffer[i] == 10) {
						linhas++;
					}
				}

				lidos = f.read(buffer, 0, BLOCKSIZE);
			}

			f.close();

			return linhas;

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return 0;

	}

	private void contarLinhasTexto() {
		// Conta quantas linhas existem no bloco atual.

		listaLinhas.clear();
		int linha = 0;
		String text = textArea.getText();
		int tam = text.length();

		for (int i = 0; i < tam; i++) {
			if (text.substring(i, i + 1).equals("\n")) {
				listaLinhas.add(linha++, i);
			}
		}

		txtLinha.setText(String.valueOf(linha));

	}

	private void exibirPosicao(int pos) {

		int linha = 1;
		for (int i = 0; i <= listaLinhas.size() - 1; i++) {
			if (pos <= listaLinhas.get(i)) {
				break;
			}
			linha++;
		}

		int x = 0;
		String caracter = "";

		if (pos < textArea.getText().length()) {
			x = (int) textArea.getText().substring(pos, pos + 1).toCharArray()[0];
			caracter = textArea.getText().substring(pos, pos + 1);
		}

		System.out.println(
				String.format("exibirPosicao-> Caret Position: [%d], Anchor Position [%d], Caracter [%s], ASC [%d]",
						textArea.getCaretPosition(), textArea.getAnchor(), caracter, x));

		int col = (pos + 1) - ((linha == 1) ? 0 : listaLinhas.get(linha - 2) + 1);
		txtLinha.setText(String.valueOf(linha));
		txtColuna.setText(String.valueOf(col));

	}

	/**
	 * Thread de controle geral.
	 */
	private void clRun() {
		System.out.println("Iniciou Contagem de Linhas em background.");

		int qtdLinhas = contarLinhas();

		System.out.println("Terminou Contagem de Linhas em background.");

		txtTotLinhas.setText(String.valueOf(qtdLinhas));

	}

	/**
	 * Thread de contagem de linhas do arquivo em background.
	 */
	private void glRun() {
		System.out.println("Iniciou globalThread.");

		long t = 0;

		while (!blnStop) {
			try {
				Thread.currentThread().sleep(100);

				verificacoes();

			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void verificacoes() {

		int pos = textArea.getCaretPosition();

		if (pos != posicaoAtual) {
			posicaoAtual = pos;
			exibirPosicao(pos);
		}

	}

}
