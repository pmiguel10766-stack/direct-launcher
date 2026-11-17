import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * DirectLauncher - Minecraft Java Edition (Vanilla / Fabric / Forge)
 * Requer Java 17+. Baixa tudo direto dos servidores oficiais.
 * Compilar: javac Main.java   |   Rodar: java Main
 */
public class Main extends JFrame {

    // ===================== CONSTANTES =====================
    static final Path BASE = Path.of(System.getProperty("user.home"), ".directlauncher");
    static final Path CONFIG = BASE.resolve("config.json");
    static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    static final String ASSETS_URL = "https://resources.download.minecraft.net/";
    static final String FORGE_META = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    static final String FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    static final String MS_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    static final String MS_SCOPE = "XboxLive.signin offline_access";
    static final String ELY = "https://authserver.ely.by/auth/";
    static final String AUTHLIB_LATEST = "https://authlib-injector.yushi.moe/artifact/latest.json";
    static final String FORM = "application/x-www-form-urlencoded";
    static final String JSON_T = "application/json";
    static final String UA = "DirectLauncher/3.1 (launcher pessoal em Java)";

    static final String OSNAME = System.getProperty("os.name").toLowerCase();
    static final String OS = OSNAME.contains("win") ? "windows" : OSNAME.contains("mac") ? "osx" : "linux";
    static final String ARCH = System.getProperty("os.arch").toLowerCase();
    static final boolean IS64 = ARCH.contains("64");
    static final boolean IS_ARM = ARCH.contains("aarch64") || ARCH.contains("arm64");

    static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    // Cores
    static final Color COR_BG = new Color(24, 28, 36);
    static final Color COR_PAINEL = new Color(33, 38, 49);
    static final Color COR_TEXTO = new Color(220, 224, 230);
    static final Color COR_CINZA = new Color(149, 165, 166);
    static final Color COR_DESTAQUE = new Color(41, 128, 185);
    static final Color COR_SEC = new Color(52, 73, 94);
    static final Color COR_JOGAR = new Color(39, 174, 96);
    static final Color COR_PARAR = new Color(192, 57, 43);
    static final Color COR_AVISO = new Color(230, 126, 34);

    record Ver(String id, String type, String url) {
        @Override public String toString() { return id; }
    }

    record LV(String id, String label) {
        @Override public String toString() { return label; }
    }

    record Dl(String url, Path dest, String sha1) {}

    record Resp(int status, String body) {}

    record Sessao(String token, String uuid, String name, long expira) {}

    /** Instância = nome + versão + loader + pasta própria (mods e saves separados). */
    static final class Inst {
        String name = "", dir = "", mc = "", loader = "Vanilla", lver = "", java = "", jvm = "";
        int ram; // 0 = usar o global

        Map<String, Object> toMap() {
            return m("name", name, "dir", dir, "mc", mc, "loader", loader, "lver", lver,
                    "java", java, "jvm", jvm, "ram", ram);
        }

        static Inst from(Map<String, Object> m) {
            Inst i = new Inst();
            i.name = nz(str(m.get("name")));
            i.dir = nz(str(m.get("dir")));
            i.mc = nz(str(m.get("mc")));
            i.loader = m.get("loader") == null ? "Vanilla" : str(m.get("loader"));
            i.lver = nz(str(m.get("lver")));
            i.java = nz(str(m.get("java")));
            i.jvm = nz(str(m.get("jvm")));
            i.ram = m.get("ram") instanceof Number n ? n.intValue() : 0;
            return i;
        }

        String resumo() { return mc + ("Vanilla".equals(loader) ? "" : "  " + loader); }
    }

    /** Círculo com a inicial do jogador. */
    static final class Avatar extends JComponent {
        String letra = "?";
        Color cor = COR_DESTAQUE;
        BufferedImage rosto;

        Avatar() { setPreferredSize(new Dimension(38, 38)); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (rosto != null) {
                g.setClip(new RoundRectangle2D.Float(1, 1, 35, 35, 12, 12));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(rosto, 1, 1, 36, 36, null);
                g.dispose();
                return;
            }
            g.setColor(cor);
            g.fillOval(1, 1, 35, 35);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 17));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(letra, (37 - fm.stringWidth(letra)) / 2, (37 + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    // ===================== ESTADO =====================
    private final List<Ver> todas = new ArrayList<>();
    private final List<Inst> instancias = new ArrayList<>();
    private Inst atual;
    private final Map<String, Object> cfg = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile Sessao sessao;
    private volatile Process jogo;
    private volatile boolean pararam;
    private static List<String> forgeMeta;
    private Image logo;
    private final String modo, alvoNome;
    private JDialog splash;
    private JLabel splashStatus;
    private JProgressBar splashBarra;

    // ===================== UI =====================
    private Avatar avatar;
    private JLabel lblNome, lblTipo, lblInstNome, lblInstInfo, lblStatus;
    private JProgressBar barra;
    private JButton btnJogar, btnEditar, btnLista, btnConta, btnResolver;

    public Main() { this(null, null); }

    /** modo: null = normal | "play" = abre o launcher e já inicia a instância | "direct" = só o jogo, sem janela do launcher. */
    public Main(String modo, String alvoNome) {
        this.modo = modo;
        this.alvoNome = alvoNome;
        try { Files.createDirectories(BASE); } catch (IOException ignored) {}
        lerConfig();
        carregarInstancias();

        setTitle("DirectLauncher - Minecraft Java Edition");
        setSize(980, 640);
        setMinimumSize(new Dimension(860, 540));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        logo = aplicarIcone();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { aoFechar(); }
        });

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(COR_BG);
        setContentPane(raiz);
        raiz.add(construirTopo(), BorderLayout.NORTH);
        raiz.add(construirEsquerda(), BorderLayout.WEST);
        raiz.add(construirCentro(), BorderLayout.CENTER);
        raiz.add(construirRodape(), BorderLayout.SOUTH);

        atualizarConta();
        atualizarRodape();
        carregarManifesto(this::aoManifestoPronto);
        limparCacheAutomatico();
    }

    // ===================== CONSTRUÇÃO DA TELA =====================
    private JPanel construirTopo() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COR_PAINEL);
        p.setBorder(new EmptyBorder(8, 14, 8, 14));

        avatar = new Avatar();
        lblNome = new JLabel("Player");
        lblNome.setForeground(Color.WHITE);
        lblNome.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTipo = new JLabel("");
        lblTipo.setForeground(COR_CINZA);
        lblTipo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JPanel nomes = new JPanel();
        nomes.setLayout(new BoxLayout(nomes, BoxLayout.Y_AXIS));
        nomes.setOpaque(false);
        nomes.add(lblNome);
        nomes.add(lblTipo);
        JPanel esq = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        esq.setOpaque(false);
        esq.add(avatar);
        esq.add(nomes);

        btnConta = new JButton("Conta");
        estilizarBotao(btnConta, COR_SEC);
        JButton btnCfg = new JButton("Configuração");
        estilizarBotao(btnCfg, COR_SEC);
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        dir.setOpaque(false);
        JButton btnPainel = new JButton("Painel");
        estilizarBotao(btnPainel, COR_SEC);
        JButton btnSkin = new JButton("Editar skin");
        estilizarBotao(btnSkin, COR_SEC);
        dir.add(btnPainel);
        dir.add(btnSkin);
        dir.add(btnConta);
        dir.add(btnCfg);
        btnPainel.addActionListener(e -> abrirPainel());
        btnSkin.addActionListener(e -> abrirEditorSkin());

        p.add(esq, BorderLayout.WEST);
        p.add(dir, BorderLayout.EAST);
        btnConta.addActionListener(e -> menuConta().show(btnConta, btnConta.getWidth() - 230, btnConta.getHeight()));
        btnCfg.addActionListener(e -> abrirConfiguracoes());
        return p;
    }

    private JPanel construirEsquerda() {
        JPanel col = new JPanel(new GridLayout(0, 1, 0, 10));
        col.setOpaque(false);
        JButton b1 = botaoLateral("EXECUTAR UM<br>ARQUIVO .JAR");
        JButton b2 = botaoLateral("COMPARTILHAR ARQUIVO<br>DE REGISTRO");
        JButton b3 = botaoLateral("ABRIR DIRETÓRIO<br>DO JOGO");
        col.add(b1);
        col.add(b2);
        col.add(b3);
        JButton b4 = botaoLateral("CRIAR ATALHO:<br>LAUNCHER + MINECRAFT");
        b4.setToolTipText("Atalho na Área de Trabalho que abre o DirectLauncher e já inicia o Minecraft");
        JButton b5 = botaoLateral("CRIAR ATALHO:<br>SÓ O MINECRAFT");
        b5.setToolTipText("Atalho na Área de Trabalho que abre só o Minecraft, sem a janela do DirectLauncher");
        col.add(b4);
        col.add(b5);
        JButton b6 = botaoLateral("RESOLVER<br>PROBLEMAS");
        b6.setToolTipText("Descobre o que deu errado no Minecraft e tenta corrigir");
        col.add(b6);
        JButton b7 = botaoLateral("BUSCAR<br>MODS");
        b7.setToolTipText("Procurar e instalar mods compatíveis com esta instância");
        col.add(b7);
        b7.addActionListener(e -> abrirBuscaMods());
        b1.addActionListener(e -> executarJar());
        b2.addActionListener(e -> compartilharLog());
        b3.addActionListener(e -> abrirDiretorioDoJogo());
        b4.addActionListener(e -> criarAtalho(false));
        b5.addActionListener(e -> criarAtalho(true));
        b6.addActionListener(e -> abrirResolver(atual));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(COR_BG);
        wrap.setBorder(new EmptyBorder(16, 14, 14, 0));
        wrap.setPreferredSize(new Dimension(250, 0));
        wrap.add(col, BorderLayout.NORTH);
        return wrap;
    }

    private JButton botaoLateral(String htmlTexto) {
        JButton b = new JButton("<html><div style='text-align:center'>" + htmlTexto + "</div></html>");
        estilizarBotao(b, COR_SEC);
        b.setPreferredSize(new Dimension(220, 50));
        return b;
    }

    private JPanel construirCentro() {
        JPanel c = new JPanel(new BorderLayout());
        c.setBackground(COR_BG);
        c.setBorder(new EmptyBorder(14, 14, 10, 14));

        JLabel big = new JLabel(new ImageIcon(logo.getScaledInstance(170, 170, Image.SCALE_SMOOTH)));
        big.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel titulo = new JLabel("DirectLauncher");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("Minecraft Java Edition  \u2022  Vanilla  \u2022  Forge  \u2022  Fabric");
        sub.setForeground(COR_CINZA);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel meio = new JPanel();
        meio.setLayout(new BoxLayout(meio, BoxLayout.Y_AXIS));
        meio.setOpaque(false);
        meio.add(Box.createVerticalGlue());
        meio.add(big);
        meio.add(Box.createRigidArea(new Dimension(0, 8)));
        meio.add(titulo);
        meio.add(Box.createRigidArea(new Dimension(0, 4)));
        meio.add(sub);
        meio.add(Box.createVerticalGlue());

        barra = new JProgressBar(0, 100);
        barra.setStringPainted(true);
        barra.setForeground(COR_DESTAQUE);
        barra.setBackground(COR_PAINEL);
        lblStatus = new JLabel("Status: carregando versões...", SwingConstants.CENTER);
        lblStatus.setForeground(COR_CINZA);
        JPanel sul = new JPanel(new BorderLayout(0, 6));
        sul.setOpaque(false);
        sul.add(lblStatus, BorderLayout.NORTH);
        sul.add(barra, BorderLayout.SOUTH);

        c.add(meio, BorderLayout.CENTER);
        c.add(sul, BorderLayout.SOUTH);
        return c;
    }

    private JPanel construirRodape() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(COR_PAINEL);
        p.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel lblLogo = new JLabel(new ImageIcon(logo.getScaledInstance(48, 48, Image.SCALE_SMOOTH)));
        lblInstNome = new JLabel("Minecraft");
        lblInstNome.setForeground(Color.WHITE);
        lblInstNome.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblInstInfo = new JLabel("");
        lblInstInfo.setForeground(COR_CINZA);
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.add(lblInstNome);
        info.add(lblInstInfo);
        JPanel esq = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        esq.setOpaque(false);
        esq.add(lblLogo);
        esq.add(info);

        btnResolver = new JButton("RESOLVER");
        estilizarBotao(btnResolver, COR_AVISO);
        btnResolver.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnResolver.setPreferredSize(new Dimension(130, 48));
        btnResolver.setToolTipText("Descobrir o que deu errado e tentar corrigir");
        btnResolver.setVisible(false);
        btnJogar = new JButton("JOGAR");
        estilizarBotao(btnJogar, COR_JOGAR);
        btnJogar.setFont(new Font("Segoe UI", Font.BOLD, 17));
        btnJogar.setPreferredSize(new Dimension(190, 48));
        btnEditar = new JButton("EDITAR");
        estilizarBotao(btnEditar, COR_SEC);
        btnEditar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnEditar.setPreferredSize(new Dimension(92, 48));
        btnEditar.setToolTipText("Configurar esta instância");
        btnLista = new JButton("^");
        estilizarBotao(btnLista, COR_SEC);
        btnLista.setFont(new Font("Segoe UI", Font.BOLD, 20));
        btnLista.setPreferredSize(new Dimension(48, 48));
        btnLista.setToolTipText("Lista de instâncias");
        JPanel dir = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        dir.setOpaque(false);
        dir.add(btnResolver);
        dir.add(btnJogar);
        dir.add(btnEditar);
        dir.add(btnLista);

        p.add(esq, BorderLayout.WEST);
        p.add(dir, BorderLayout.EAST);

        btnJogar.addActionListener(e -> aoJogar());
        btnEditar.addActionListener(e -> {
            if (jogoRodando()) { status("pare o jogo antes de editar a instância."); return; }
            abrirDialogoInstancia(atual);
        });
        btnLista.addActionListener(e -> mostrarListaInstancias());
        btnResolver.addActionListener(e -> abrirResolver(atual));
        return p;
    }

    private void atualizarRodape() {
        if (atual == null) {
            lblInstNome.setText("Nenhuma instância");
            lblInstInfo.setText("Clique em  ^  e depois em \"Criar nova instância\"");
        } else {
            lblInstNome.setText(atual.name);
            lblInstInfo.setText("Minecraft " + atual.resumo());
        }
        btnEditar.setEnabled(atual != null);
    }

    private void mostrarListaInstancias() {
        JPopupMenu menu = new JPopupMenu();
        for (Inst i : instancias) {
            JMenuItem it = new JMenuItem((i == atual ? ">  " : "     ") + i.name + "     \u2014  " + i.resumo());
            it.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            it.addActionListener(e -> { atual = i; atualizarRodape(); salvarConfig(); });
            menu.add(it);
        }
        if (!instancias.isEmpty()) menu.addSeparator();
        JMenuItem novo = new JMenuItem("+   Criar nova instância");
        novo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        novo.addActionListener(e -> abrirDialogoInstancia(null));
        menu.add(novo);
        Dimension d = menu.getPreferredSize();
        menu.show(btnLista, btnLista.getWidth() - d.width, -d.height);
    }

    // ===================== INSTÂNCIAS =====================
    private void carregarInstancias() {
        if (cfg.get("instances") instanceof List<?> l)
            for (Object x : l) instancias.add(Inst.from(map(x)));
        if (instancias.isEmpty() && cfg.get("version") != null && !cfgBool("migrated", false)) { // migração da versão anterior
            Inst n = new Inst();
            n.mc = cfgStr("version", "");
            n.loader = cfgStr("loader", "Vanilla");
            n.lver = cfgStr("loaderVersion", "");
            n.dir = nomeInstancia(n.mc, n.loader);
            n.name = n.mc + ("Vanilla".equals(n.loader) ? "" : " " + n.loader);
            instancias.add(n);
        }
        boolean limpou = false; // chaves antigas fariam a instância excluída voltar ao reabrir
        for (String k : new String[]{"version", "loader", "loaderVersion"}) if (cfg.remove(k) != null) limpou = true;
        if (limpou) { cfg.put("migrated", true); gravarCfg(); }
        for (Inst i : instancias) {
            if (i.name.isBlank()) i.name = nomeUnico(nomeAuto(i.mc, i.loader), i);
            if (i.dir.isBlank()) i.dir = pastaUnica(i.name);
        }
        String sel = cfgStr("instance", "");
        for (Inst i : instancias) if (i.name.equals(sel)) atual = i;
        if (atual == null && !instancias.isEmpty()) atual = instancias.get(0);
        if (cfg.get("accType") == null) cfg.put("accType", cfg.get("ms_refresh") != null ? "microsoft" : "demo");
    }

    static String nomeInstancia(String mc, String loader) {
        return mc + ("Fabric".equals(loader) ? "-fabric" : "Forge".equals(loader) ? "-forge" : "");
    }

    private String pastaUnica(String nome) {
        String base = nome.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (base.isEmpty()) base = "instancia";
        String cand = base;
        int n = 2;
        while (true) {
            final String c = cand;
            boolean usado = Files.exists(BASE.resolve("instances").resolve(c))
                    || instancias.stream().anyMatch(i -> i.dir.equalsIgnoreCase(c));
            if (!usado) return cand;
            cand = base + "-" + n++;
        }
    }

    /** Criar (editar == null) ou configurar uma instância: versão, Forge/Fabric/normal e opções próprias. */
    private void abrirDialogoInstancia(Inst editar) {
        if (todas.isEmpty()) { status("aguarde a lista de versões carregar..."); return; }
        JDialog d = new JDialog(this, editar == null ? "Criar nova instância" : "Configurar instância", true);
        JPanel raiz = new JPanel();
        raiz.setLayout(new BoxLayout(raiz, BoxLayout.Y_AXIS));
        raiz.setBackground(COR_BG);
        raiz.setBorder(new EmptyBorder(14, 14, 14, 14));
        d.setContentPane(raiz);

        JTextField txtNome = campo(editar != null ? editar.name : "");
        JComboBox<String> cbLoader = new JComboBox<>(new String[]{"Vanilla", "Forge", "Fabric"});
        if (editar != null) cbLoader.setSelectedItem(editar.loader);
        JCheckBox cR = chk("Release", cfgBool("release", true));
        JCheckBox cS = chk("Snapshot", cfgBool("snapshot", false));
        JCheckBox cA = chk("Antigas (Alpha/Beta)", cfgBool("antigas", false));
        JList<Ver> lst = new JList<>();
        lst.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lst.setBackground(COR_PAINEL);
        lst.setForeground(COR_TEXTO);
        lst.setSelectionBackground(COR_DESTAQUE);
        lst.setSelectionForeground(Color.WHITE);
        JScrollPane sp = new JScrollPane(lst);
        sp.setPreferredSize(new Dimension(500, 170));
        sp.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 90)));
        JComboBox<LV> cbLV = new JComboBox<>();
        cbLV.setEnabled(false);
        JTextField txtRam = campo(editar != null && editar.ram > 0 ? String.valueOf(editar.ram) : "");
        JTextField txtJava = campo(editar != null ? editar.java : "");
        JTextField txtJvm = campo(editar != null ? editar.jvm : "");
        txtRam.setToolTipText("Vazio = usar o valor global (Configuração > Ajustes do Java)");
        txtJava.setToolTipText("Vazio = automático / valor global");
        txtJvm.setToolTipText("Argumentos extras da JVM só desta instância");

        String lverPre = editar != null ? editar.lver : "";
        String[] mcPre = {editar != null ? editar.mc : "1.12.2"};
        if (editar != null) {
            for (Ver v : todas) {
                if (!v.id().equals(editar.mc)) continue;
                if ("snapshot".equals(v.type())) cS.setSelected(true);
                else if (!"release".equals(v.type())) cA.setSelected(true);
            }
        }

        AtomicInteger gen = new AtomicInteger();
        Runnable recarregarLoader = () -> {
            String tipo = (String) cbLoader.getSelectedItem();
            Ver v = lst.getSelectedValue();
            int g = gen.incrementAndGet();
            boolean usa = !"Vanilla".equals(tipo) && v != null;
            cbLV.setEnabled(usa);
            if (!usa) { cbLV.setModel(new DefaultComboBoxModel<>()); return; }
            cbLV.setModel(new DefaultComboBoxModel<>(new LV[]{new LV("", "carregando...")}));
            new Thread(() -> {
                List<LV> lista = new ArrayList<>();
                try {
                    lista = "Fabric".equals(tipo) ? fabricVersoes(v.id()) : forgeVersoes(v.id());
                } catch (Exception e) {
                    status("erro ao listar versões do " + tipo + ": " + e.getMessage());
                }
                final List<LV> fim = lista;
                SwingUtilities.invokeLater(() -> {
                    if (g != gen.get()) return;
                    DefaultComboBoxModel<LV> mdl = new DefaultComboBoxModel<>();
                    for (LV x : fim) mdl.addElement(x);
                    cbLV.setModel(mdl);
                    int sel = 0;
                    for (int i = 0; i < fim.size(); i++)
                        if (fim.get(i).label().contains("recommended")) { sel = i; break; }
                    for (int i = 0; i < fim.size(); i++)
                        if (fim.get(i).id().equals(lverPre)) { sel = i; break; }
                    if (!fim.isEmpty()) cbLV.setSelectedIndex(sel);
                });
            }).start();
        };
        Runnable filtrarLista = () -> {
            Ver sel = lst.getSelectedValue();
            String alvo = sel != null ? sel.id() : mcPre[0];
            DefaultListModel<Ver> mdl = new DefaultListModel<>();
            for (Ver v : todas) {
                boolean ok = switch (v.type()) {
                    case "release" -> cR.isSelected();
                    case "snapshot" -> cS.isSelected();
                    default -> cA.isSelected();
                };
                if (ok) mdl.addElement(v);
            }
            lst.setModel(mdl);
            for (int i = 0; i < mdl.size(); i++) {
                if (mdl.get(i).id().equals(alvo)) {
                    lst.setSelectedIndex(i);
                    lst.ensureIndexIsVisible(i);
                    break;
                }
            }
        };
        boolean[] nomeManual = {editar != null};
        boolean[] programatico = {false};
        txtNome.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void mudou() { if (!programatico[0]) nomeManual[0] = !txtNome.getText().isBlank(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { mudou(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { mudou(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { mudou(); }
        });
        Runnable sugerirNome = () -> {
            Ver v = lst.getSelectedValue();
            if (nomeManual[0] || v == null) return;
            programatico[0] = true;
            txtNome.setText(nomeUnico(nomeAuto(v.id(), (String) cbLoader.getSelectedItem()), editar));
            programatico[0] = false;
        };
        for (JCheckBox c : new JCheckBox[]{cR, cS, cA}) c.addActionListener(e -> filtrarLista.run());
        lst.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) { recarregarLoader.run(); sugerirNome.run(); } });
        cbLoader.addActionListener(e -> { recarregarLoader.run(); sugerirNome.run(); });

        raiz.add(fila("Nome da instância:", txtNome));
        raiz.add(Box.createRigidArea(new Dimension(0, 8)));
        raiz.add(fila("Tipo:", cbLoader));
        raiz.add(Box.createRigidArea(new Dimension(0, 8)));
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filtros.setOpaque(false);
        filtros.add(rotulo("Versões do Minecraft:"));
        filtros.add(cR);
        filtros.add(cS);
        filtros.add(cA);
        filtros.setAlignmentX(Component.LEFT_ALIGNMENT);
        raiz.add(filtros);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        raiz.add(sp);
        raiz.add(Box.createRigidArea(new Dimension(0, 8)));
        raiz.add(fila("Versão do Forge/Fabric:", cbLV));
        raiz.add(Box.createRigidArea(new Dimension(0, 12)));
        JLabel adv = rotulo("Avançado (vazio = usar o global)");
        adv.setFont(new Font("Segoe UI", Font.BOLD, 12));
        adv.setAlignmentX(Component.LEFT_ALIGNMENT);
        raiz.add(adv);
        raiz.add(Box.createRigidArea(new Dimension(0, 6)));
        raiz.add(fila("RAM (MB):", txtRam));
        raiz.add(Box.createRigidArea(new Dimension(0, 6)));
        raiz.add(fila("Java (caminho):", txtJava));
        raiz.add(Box.createRigidArea(new Dimension(0, 6)));
        raiz.add(fila("Argumentos JVM:", txtJvm));
        raiz.add(Box.createVerticalGlue());
        raiz.add(Box.createRigidArea(new Dimension(0, 12)));

        JButton ok = new JButton(editar == null ? "CRIAR" : "SALVAR");
        estilizarBotao(ok, COR_JOGAR);
        JButton cancelar = new JButton("CANCELAR");
        estilizarBotao(cancelar, COR_SEC);
        JButton excluir = new JButton("EXCLUIR");
        estilizarBotao(excluir, COR_PARAR);
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botoes.setOpaque(false);
        if (editar != null) botoes.add(excluir);
        botoes.add(cancelar);
        botoes.add(ok);
        botoes.setAlignmentX(Component.LEFT_ALIGNMENT);
        raiz.add(botoes);

        cancelar.addActionListener(e -> d.dispose());
        excluir.addActionListener(e -> {
            Object[] ops = {"Só remover da lista", "Remover e apagar a pasta (saves e mods)", "Cancelar"};
            int r = JOptionPane.showOptionDialog(d, "O que fazer com \"" + editar.name + "\"?\n\nPasta: " + pastaDoJogo(editar),
                    "Excluir instância", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, ops, ops[0]);
            if (r != 0 && r != 1) return;
            if (r == 1) {
                boolean outra = instancias.stream().anyMatch(x -> x != editar && x.dir.equalsIgnoreCase(editar.dir));
                if (outra) {
                    aviso(d, "Outra instância usa a mesma pasta, então só removi da lista.");
                } else {
                    int c = JOptionPane.showConfirmDialog(d, "Apagar PARA SEMPRE os saves, mods e configurações desta instância?",
                            "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (c != JOptionPane.YES_OPTION) return;
                    try {
                        apagarArvore(pastaDoJogo(editar), BASE.resolve("instances"));
                    } catch (IOException ex) {
                        aviso(d, "Não consegui apagar a pasta: " + ex.getMessage());
                    }
                }
            }
            instancias.remove(editar);
            atual = instancias.isEmpty() ? null : instancias.get(0);
            if (atual == null) cfg.remove("instance");
            salvarConfig();
            atualizarRodape();
            status("instância removida.");
            d.dispose();
        });
        ok.addActionListener(e -> {
            Ver v = lst.getSelectedValue();
            if (v == null) { aviso(d, "Escolha uma versão do Minecraft."); return; }
            String loader = (String) cbLoader.getSelectedItem();
            LV lv = (LV) cbLV.getSelectedItem();
            if (!"Vanilla".equals(loader) && (lv == null || lv.id().isEmpty())) {
                aviso(d, "Escolha a versão do " + loader + " (ou use o tipo Vanilla)."); return;
            }
            String digitado = txtNome.getText().trim();
            String nome = digitado.isEmpty() ? nomeUnico(nomeAuto(v.id(), loader), editar) : digitado;
            for (Inst x : instancias)
                if (x != editar && x.name.equalsIgnoreCase(nome)) { aviso(d, "Já existe uma instância com esse nome."); return; }
            int ram = 0;
            if (!txtRam.getText().trim().isEmpty()) {
                try { ram = Integer.parseInt(txtRam.getText().trim()); } catch (NumberFormatException ex) { ram = -1; }
                if (ram < 512) { aviso(d, "RAM inválida (mínimo 512 MB)."); return; }
            }
            final int ramF = ram;
            final String lverNovo = "Vanilla".equals(loader) ? "" : lv.id();
            boolean mudouVersao = editar != null && (!editar.mc.equals(v.id()) || !editar.loader.equals(loader)
                    || !editar.lver.equals(lverNovo));
            Runnable aplicar = () -> {
                Inst n = editar != null ? editar : new Inst();
                n.name = nome;
                n.mc = v.id();
                n.loader = loader;
                n.lver = lverNovo;
                n.ram = ramF;
                n.java = txtJava.getText().trim();
                n.jvm = txtJvm.getText().trim();
                if (editar == null) {
                    n.dir = pastaUnica(nome);
                    instancias.add(n);
                }
                atual = n;
                salvarConfig();
                atualizarRodape();
                d.dispose();
            };
            if (!mudouVersao) { aplicar.run(); return; }
            Object[] ops = {"Fazer backup e salvar", "Salvar sem backup", "Cancelar"};
            int resp = JOptionPane.showOptionDialog(d, "Você está trocando a versão ou o loader desta instância.\n\n"
                            + "Fazer backup de mundos, mods e config antes?\n"
                            + "(Mods feitos para a versão antiga podem não funcionar na nova; se der erro, use Resolver > Testar sem mods.)",
                    "Atualizar instância", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
            if (resp != 0 && resp != 1) return;
            if (resp == 1) { aplicar.run(); return; }
            if (jogoRodando()) { aviso(d, "Pare o jogo antes de atualizar a instância."); return; }
            ok.setEnabled(false);
            status("fazendo backup antes de atualizar...");
            new Thread(() -> {
                try {
                    backupCompleto(editar);
                    SwingUtilities.invokeLater(() -> { status("backup feito; instância atualizada."); aplicar.run(); });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        int c = JOptionPane.showConfirmDialog(d, "O backup falhou: " + ex.getMessage()
                                + "\n\nSalvar a mudança mesmo assim?", "Backup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (c == JOptionPane.YES_OPTION) aplicar.run(); else ok.setEnabled(true);
                    });
                }
            }).start();
        });

        filtrarLista.run();
        recarregarLoader.run();
        sugerirNome.run();
        d.setSize(580, 700);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ===================== CONFIGURAÇÃO (categorias) =====================
    private void abrirConfiguracoes() {
        JDialog d = new JDialog(this, "Configuração", true);
        JPanel raiz = new JPanel(new BorderLayout(10, 0));
        raiz.setBackground(COR_BG);
        raiz.setBorder(new EmptyBorder(12, 12, 12, 12));
        d.setContentPane(raiz);

        String[] cats = {"Vídeo e renderizador", "Personalização dos controles", "Ajustes do Java",
                "Configurações diversas", "Opções experimentais"};
        JList<String> lc = new JList<>(cats);
        lc.setBackground(COR_PAINEL);
        lc.setForeground(COR_TEXTO);
        lc.setSelectionBackground(COR_DESTAQUE);
        lc.setSelectionForeground(Color.WHITE);
        lc.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lc.setFixedCellHeight(40);
        lc.setPreferredSize(new Dimension(230, 0));
        lc.setSelectedIndex(0);
        JLabel tit = rotulo("Categorias");
        tit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JPanel esq = new JPanel(new BorderLayout(0, 6));
        esq.setOpaque(false);
        esq.add(tit, BorderLayout.NORTH);
        esq.add(lc, BorderLayout.CENTER);
        raiz.add(esq, BorderLayout.WEST);

        CardLayout cl = new CardLayout();
        JPanel cards = new JPanel(cl);
        cards.setOpaque(false);
        raiz.add(cards, BorderLayout.CENTER);

        // --- Vídeo e renderizador ---
        JTextField fW = campo(cfgStr("width", ""));
        JTextField fH = campo(cfgStr("height", ""));
        JCheckBox cFull = chk("Iniciar em tela cheia", cfgBool("fullscreen", false));
        JComboBox<String> cbGpu = new JComboBox<>(new String[]{"Automático (padrão do Windows)",
                "Economia de energia (placa integrada)", "Alto desempenho (placa dedicada)"});
        cbGpu.setSelectedIndex(Math.max(0, Math.min(2, cfgInt("gpu", 0))));
        cbGpu.setEnabled(OS.equals("windows"));
        JButton bOtim = new JButton("OTIMIZAR: BAIXAR MODS DE DESEMPENHO");
        estilizarBotao(bOtim, COR_JOGAR);
        JPanel pVideo = card("Vídeo e renderizador", "Resolução, renderização e desempenho");
        pVideo.add(fila("Largura (px):", fW));
        pVideo.add(Box.createRigidArea(new Dimension(0, 6)));
        pVideo.add(fila("Altura (px):", fH));
        pVideo.add(Box.createRigidArea(new Dimension(0, 6)));
        pVideo.add(esquerda(cFull));
        pVideo.add(nota("Deixe largura e altura vazias para usar o tamanho padrão do jogo."));
        pVideo.add(subtitulo("Renderizador"));
        pVideo.add(nota("<b>OpenGL</b> \u2013 o padrão de todas as versões.<br>"
                + "<b>Vulkan (experimental)</b> \u2013 só em versões novas (série 26.x); a troca é feita dentro do jogo, em Opções > Vídeo, quando existir.<br>"
                + "<b>Sodium / Embeddium / Rubidium</b> \u2013 mods que trocam o desenho por um mais rápido; o botão Otimizar baixa o certo para a sua instância."));
        pVideo.add(Box.createRigidArea(new Dimension(0, 8)));
        pVideo.add(fila("Placa de vídeo:", cbGpu));
        pVideo.add(nota("Define qual placa o Windows usa para o Java do Minecraft (vale para o próximo início do jogo)."));
        pVideo.add(subtitulo("Otimização"));
        pVideo.add(nota("Baixa da Modrinth os mods de desempenho que combinam com a <b>versão do Minecraft</b> e o <b>loader</b> "
                + "da instância selecionada. Fabric: Sodium, Lithium, FerriteCore, ModernFix, EntityCulling, ImmediatelyFast. "
                + "Forge: Embeddium (ou Rubidium), FerriteCore, ModernFix, EntityCulling. Versões muito antigas têm poucas opções."));
        pVideo.add(Box.createRigidArea(new Dimension(0, 8)));
        pVideo.add(larga(bOtim));
        bOtim.addActionListener(e -> {
            Inst in = atual;
            if (in == null) { aviso(d, "Crie ou selecione uma instância primeiro."); return; }
            boolean vanilla = "Vanilla".equals(in.loader);
            Object[] ops = vanilla ? new Object[]{"Ajustar JVM", "Cancelar"}
                    : new Object[]{"Baixar mods", "Baixar mods + ajustar JVM", "Cancelar"};
            String msg = vanilla
                    ? "A instância \"" + in.name + "\" é Vanilla (sem mods).\nMods de desempenho precisam de Fabric ou Forge.\n\n"
                    + "Quer aplicar só os ajustes recomendados de memória/JVM?"
                    : "Instância: " + in.name + "  (" + in.resumo() + ")\n\nBaixar da Modrinth os mods de desempenho compatíveis com "
                    + "esta versão e este loader?\nOs arquivos vão para a pasta mods da instância.";
            int r = JOptionPane.showOptionDialog(d, msg, "Otimizar", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
            if (r < 0 || r >= ops.length - 1) return;
            boolean jvm = vanilla || r == 1;
            bOtim.setEnabled(false);
            status("otimizando " + in.name + "...");
            new Thread(() -> {
                try {
                    String res = otimizarInstancia(in, jvm);
                    status("otimização concluída.");
                    SwingUtilities.invokeLater(() -> mostrarTexto(d, "Otimização de " + in.name, res));
                } catch (Exception ex) {
                    status("erro ao otimizar: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> aviso(d, "Não deu certo: " + ex.getMessage()));
                } finally {
                    SwingUtilities.invokeLater(() -> bOtim.setEnabled(true));
                }
            }).start();
        });
        cards.add(rolagem(pVideo), cats[0]);

        // --- Controles ---
        JComboBox<String> cbGui = new JComboBox<>(new String[]{"Automático", "1", "2", "3", "4"});
        String gs = cfgStr("guiScale", "0");
        cbGui.setSelectedIndex(gs.matches("[0-4]") ? Integer.parseInt(gs) : 0);
        JPanel pCtl = card("Personalização dos controles", "Gestos, botões e escala");
        pCtl.add(fila("Escala da interface do jogo:", cbGui));
        pCtl.add(Box.createRigidArea(new Dimension(0, 10)));
        pCtl.add(nota("Gestos e botões na tela são recursos de celular e não se aplicam ao PC. "
                + "Aqui, a escala (guiScale) é gravada no options.txt de cada instância ao jogar. "
                + "Teclas e mouse se ajustam dentro do jogo, em Opções > Controles."));
        cards.add(pCtl, cats[1]);

        // --- Java ---
        JTextField fJava = campo(cfgStr("java", ""));
        fJava.setToolTipText("Vazio = automático (acha ou baixa o Java certo)");
        JTextField fRam = campo(cfgStr("ram", "2048"));
        JTextField fJvm = campo(cfgStr("jvmArgs", ""));
        JComboBox<String> cbJava = new JComboBox<>(new String[]{"(clique em Detectar)"});
        JButton bDet = new JButton("Detectar");
        estilizarBotao(bDet, COR_SEC);
        List<Path> achados = new ArrayList<>();
        bDet.addActionListener(e -> {
            achados.clear();
            achados.addAll(javasInstalados());
            DefaultComboBoxModel<String> mdl = new DefaultComboBoxModel<>();
            mdl.addElement("(automático)");
            for (Path h : achados) mdl.addElement("Java " + majorDe(h) + "  \u2014  " + h);
            cbJava.setModel(mdl);
        });
        cbJava.addActionListener(e -> {
            int i = cbJava.getSelectedIndex();
            if (i == 0) fJava.setText("");
            else if (i > 0 && i - 1 < achados.size()) fJava.setText(achados.get(i - 1).toString());
        });
        JPanel linhaJava = new JPanel(new BorderLayout(6, 0));
        linhaJava.setOpaque(false);
        linhaJava.add(cbJava, BorderLayout.CENTER);
        linhaJava.add(bDet, BorderLayout.EAST);
        JPanel pJava = card("Ajustes do Java", "Java Runtimes, argumentos JVM e quantidade de RAM");
        pJava.add(fila("Java instalado:", linhaJava));
        pJava.add(Box.createRigidArea(new Dimension(0, 6)));
        pJava.add(fila("Caminho do Java:", fJava));
        pJava.add(Box.createRigidArea(new Dimension(0, 6)));
        pJava.add(fila("RAM (MB):", fRam));
        pJava.add(Box.createRigidArea(new Dimension(0, 6)));
        pJava.add(fila("Argumentos JVM:", fJvm));
        pJava.add(Box.createRigidArea(new Dimension(0, 10)));
        pJava.add(nota("Vazio = automático: versões até 1.16 usam Java 8, as novas usam 17 ou 21 (baixado sozinho se faltar, só no Windows). "
                + "O launcher não isola o jogo (sem sandbox): mods e jars rodam com as permissões do seu usuário."));
        cards.add(pJava, cats[2]);

        // --- Diversas ---
        JCheckBox mR = chk("Mostrar Release", cfgBool("release", true));
        JCheckBox mS = chk("Mostrar Snapshot", cfgBool("snapshot", false));
        JCheckBox mA = chk("Mostrar Antigas (Alpha/Beta)", cfgBool("antigas", false));
        JButton bLista = new JButton("ATUALIZAR LISTA DE VERSÕES");
        estilizarBotao(bLista, COR_DESTAQUE);
        JButton bVer = new JButton("VERIFICAR BIBLIOTECAS DA INSTÂNCIA SELECIONADA");
        estilizarBotao(bVer, COR_DESTAQUE);
        JButton bPasta = new JButton("ABRIR PASTA DO LAUNCHER");
        estilizarBotao(bPasta, COR_SEC);
        JButton bBkMundos = new JButton("BACKUP DOS MUNDOS");
        estilizarBotao(bBkMundos, COR_JOGAR);
        JButton bBkMods = new JButton("BACKUP DOS MODS E CONFIG");
        estilizarBotao(bBkMods, COR_JOGAR);
        JButton bBkPasta = new JButton("ABRIR PASTA DE BACKUPS");
        estilizarBotao(bBkPasta, COR_SEC);
        JCheckBox cAuto = chk("Backup automático dos mundos durante o jogo e ao encerrar", cfgBool("autoBackup", true));
        JComboBox<String> cbMin = new JComboBox<>(new String[]{"5 minutos", "15 minutos", "30 minutos"});
        int autoMin = cfgInt("autoMin", 15);
        cbMin.setSelectedIndex(autoMin <= 5 ? 0 : autoMin >= 30 ? 2 : 1);
        JComboBox<String> cbManter = new JComboBox<>(new String[]{"3", "5", "10"});
        int autoManter = cfgInt("autoManter", 5);
        cbManter.setSelectedIndex(autoManter <= 3 ? 0 : autoManter >= 10 ? 2 : 1);
        JCheckBox cCache = chk("Limpar o cache sozinho ao abrir o launcher (itens com mais de 7 dias)", cfgBool("autoCache", true));
        JButton bCache = new JButton("LIMPAR CACHE AGORA");
        estilizarBotao(bCache, COR_DESTAQUE);
        bLista.addActionListener(e -> carregarManifesto());
        bVer.addActionListener(e -> verificarBibliotecas(atual));
        bPasta.addActionListener(e -> abrir(BASE));
        bBkMundos.addActionListener(e -> executarBackup(d, true));
        bBkMods.addActionListener(e -> executarBackup(d, false));
        bBkPasta.addActionListener(e -> abrir(BASE.resolve("backups")));
        bCache.addActionListener(e -> limparCacheManual(d));
        JPanel pMisc = card("Configurações diversas", "Lista de versões, verificação, backups e cache");
        pMisc.add(esquerda(mR));
        pMisc.add(esquerda(mS));
        pMisc.add(esquerda(mA));
        pMisc.add(Box.createRigidArea(new Dimension(0, 8)));
        pMisc.add(larga(bLista));
        pMisc.add(Box.createRigidArea(new Dimension(0, 8)));
        pMisc.add(larga(bVer));
        pMisc.add(Box.createRigidArea(new Dimension(0, 8)));
        pMisc.add(larga(bPasta));
        pMisc.add(nota("A verificação confere o SHA1 das bibliotecas e do jogo base; arquivos corrompidos são apagados e baixados de novo no próximo Jogar."));
        pMisc.add(subtitulo("Backups"));
        pMisc.add(larga(bBkMundos));
        pMisc.add(Box.createRigidArea(new Dimension(0, 8)));
        pMisc.add(larga(bBkMods));
        pMisc.add(Box.createRigidArea(new Dimension(0, 8)));
        pMisc.add(larga(bBkPasta));
        pMisc.add(Box.createRigidArea(new Dimension(0, 8)));
        pMisc.add(esquerda(cAuto));
        pMisc.add(fila("A cada:", cbMin));
        pMisc.add(Box.createRigidArea(new Dimension(0, 6)));
        pMisc.add(fila("Manter os últimos:", cbManter));
        pMisc.add(nota("Os backups ficam em .directlauncher/backups (um por instância). O automático só guarda os mundos, "
                + "só cria um novo se algo mudou e apaga os mais antigos. Antes de trocar a versão de uma instância o launcher oferece um backup completo."));
        pMisc.add(subtitulo("Cache"));
        pMisc.add(esquerda(cCache));
        pMisc.add(Box.createRigidArea(new Dimension(0, 6)));
        pMisc.add(larga(bCache));
        pMisc.add(nota("Só apaga downloads incompletos, instaladores já usados e logs/relatórios de crash antigos. Nunca mexe em saves, mods, configurações, assets nem libraries."));
        cards.add(rolagem(pMisc), cats[3]);

        // --- Experimentais ---
        JSpinner spThreads = new JSpinner(new SpinnerNumberModel(Math.max(1, Math.min(16, cfgInt("threads", 8))), 1, 16, 1));
        JCheckBox cMin = chk("Minimizar o launcher enquanto o jogo roda", cfgBool("minimize", false));
        JCheckBox cEn = chk("Forçar idioma para o inglês", cfgBool("forceEn", false));
        JLabel aviso = new JLabel("<html>Use estas opções com cuidado, não daremos suporte</html>");
        aviso.setForeground(COR_AVISO);
        aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel pExp = card("Opções experimentais", "");
        pExp.add(aviso);
        pExp.add(Box.createRigidArea(new Dimension(0, 10)));
        pExp.add(fila("Downloads simultâneos:", spThreads));
        pExp.add(Box.createRigidArea(new Dimension(0, 6)));
        pExp.add(esquerda(cMin));
        pExp.add(Box.createRigidArea(new Dimension(0, 6)));
        pExp.add(esquerda(cEn));
        pExp.add(nota("Permite que você veja frases originais, como esperado por desenvolvedores. "
                + "Aqui vale para o idioma do jogo (grava lang:en_us no options.txt). O texto do launcher só existe em português."));
        cards.add(pExp, cats[4]);

        lc.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lc.getSelectedValue() != null) cl.show(cards, lc.getSelectedValue());
        });
        cl.show(cards, cats[0]);

        JButton salvar = new JButton("SALVAR");
        estilizarBotao(salvar, COR_JOGAR);
        JButton fechar = new JButton("CANCELAR");
        estilizarBotao(fechar, COR_SEC);
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rodape.setOpaque(false);
        rodape.setBorder(new EmptyBorder(10, 0, 0, 0));
        rodape.add(fechar);
        rodape.add(salvar);
        raiz.add(rodape, BorderLayout.SOUTH);

        fechar.addActionListener(e -> d.dispose());
        salvar.addActionListener(e -> {
            String w = fW.getText().trim(), h = fH.getText().trim();
            if ((!w.isEmpty() && !w.matches("\\d{3,5}")) || (!h.isEmpty() && !h.matches("\\d{3,5}"))) {
                aviso(d, "Largura e altura devem ser números (ou deixe vazio)."); return;
            }
            String r = fRam.getText().trim();
            if (!r.matches("\\d{3,6}") || Integer.parseInt(r) < 512) { aviso(d, "RAM inválida (mínimo 512 MB)."); return; }
            cfg.put("width", w);
            cfg.put("height", h);
            cfg.put("fullscreen", cFull.isSelected());
            int novoGpu = cbGpu.getSelectedIndex();
            boolean gpuMudou = novoGpu != cfgInt("gpu", 0);
            cfg.put("gpu", String.valueOf(novoGpu));
            cfg.put("guiScale", String.valueOf(cbGui.getSelectedIndex()));
            cfg.put("java", fJava.getText().trim());
            cfg.put("ram", r);
            cfg.put("jvmArgs", fJvm.getText().trim());
            cfg.put("release", mR.isSelected());
            cfg.put("snapshot", mS.isSelected());
            cfg.put("antigas", mA.isSelected());
            cfg.put("threads", String.valueOf(spThreads.getValue()));
            cfg.put("minimize", cMin.isSelected());
            cfg.put("forceEn", cEn.isSelected());
            cfg.put("autoBackup", cAuto.isSelected());
            cfg.put("autoMin", String.valueOf(new int[]{5, 15, 30}[cbMin.getSelectedIndex()]));
            cfg.put("autoManter", String.valueOf(new int[]{3, 5, 10}[cbManter.getSelectedIndex()]));
            cfg.put("autoCache", cCache.isSelected());
            salvarConfig();
            status("configuração salva.");
            if (gpuMudou && OS.equals("windows"))
                new Thread(() -> {
                    try { status(aplicarGpu(novoGpu).split("\n")[0]); } catch (Exception ex) { status("placa de vídeo: " + ex.getMessage()); }
                }).start();
            d.dispose();
        });

        d.setSize(880, 640);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private JPanel card(String titulo, String desc) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(COR_PAINEL);
        p.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel t = new JLabel(titulo);
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Segoe UI", Font.BOLD, 17));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(t);
        if (!desc.isEmpty()) {
            JLabel s = new JLabel(desc);
            s.setForeground(COR_CINZA);
            s.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(s);
        }
        p.add(Box.createRigidArea(new Dimension(0, 14)));
        return p;
    }

    private JLabel nota(String texto) {
        JLabel l = new JLabel("<html><div style='width:430px'>" + texto + "</div></html>");
        l.setForeground(COR_CINZA);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel fila(String r, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        JLabel l = rotulo(r);
        l.setPreferredSize(new Dimension(170, 28));
        p.add(l, BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel esquerda(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        p.setOpaque(false);
        p.add(c);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel larga(JButton b) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(b, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JTextField campo(String t) {
        JTextField c = new JTextField(t);
        estilizarCampo(c);
        return c;
    }

    private JCheckBox chk(String t, boolean sel) {
        JCheckBox c = new JCheckBox(t, sel);
        c.setOpaque(false);
        c.setForeground(COR_TEXTO);
        c.setFocusPainted(false);
        return c;
    }

    private void aviso(Component pai, String msg) {
        JOptionPane.showMessageDialog(pai, msg, "Atenção", JOptionPane.WARNING_MESSAGE);
    }

    // ===================== CONFIG (arquivo) =====================
    private void lerConfig() {
        try {
            if (Files.exists(CONFIG)) cfg.putAll(map(Json.parse(Files.readString(CONFIG))));
        } catch (Exception ignored) {}
    }

    private void gravarCfg() {
        try {
            synchronized (cfg) { Files.writeString(CONFIG, Json.write(cfg)); }
        } catch (IOException ignored) {}
    }

    private void salvarConfig() {
        List<Object> l = new ArrayList<>();
        for (Inst i : instancias) l.add(i.toMap());
        cfg.put("instances", l);
        if (atual != null) cfg.put("instance", atual.name);
        gravarCfg();
    }

    private String cfgStr(String k, String def) {
        Object o = cfg.get(k);
        return o == null ? def : o.toString();
    }

    private boolean cfgBool(String k, boolean def) {
        Object o = cfg.get(k);
        return o instanceof Boolean b ? b : def;
    }

    private int cfgInt(String k, int def) {
        try { return Integer.parseInt(cfgStr(k, "")); } catch (NumberFormatException e) { return def; }
    }

    // ===================== CONTAS =====================
    private String contaTipo() { return cfgStr("accType", "demo"); }

    private String nomeJogador() {
        return switch (contaTipo()) {
            case "microsoft" -> cfgStr("ms_name", "Microsoft");
            case "elyby" -> cfgStr("ely_name", "ely.by");
            default -> cfgStr("user", "Player");
        };
    }

    private void atualizarConta() {
        SwingUtilities.invokeLater(() -> {
            String tipo = contaTipo();
            String nome = nomeJogador();
            lblNome.setText(nome);
            avatar.letra = nome.isEmpty() ? "?" : nome.substring(0, 1).toUpperCase();
            switch (tipo) {
                case "microsoft" -> { lblTipo.setText("Conta Microsoft"); avatar.cor = new Color(0, 120, 215); }
                case "elyby" -> { lblTipo.setText("Conta ely.by"); avatar.cor = new Color(46, 160, 67); }
                default -> { lblTipo.setText("Modo demo (offline)"); avatar.cor = new Color(127, 140, 141); }
            }
            avatar.rosto = "demo".equals(tipo) ? carregarRosto() : null;
            avatar.repaint();
        });
    }

    private JPopupMenu menuConta() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem ms = new JMenuItem("Entrar com Microsoft");
        JMenuItem ely = new JMenuItem("Entrar com ely.by");
        JMenuItem demo = new JMenuItem("Modo demo (offline)...");
        JMenuItem sair = new JMenuItem("Sair da conta");
        for (JMenuItem i : new JMenuItem[]{ms, ely, demo, sair}) i.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ms.addActionListener(e -> iniciarLoginMicrosoft());
        ely.addActionListener(e -> dialogoEly());
        demo.addActionListener(e -> dialogoDemo());
        sair.addActionListener(e -> sairConta());
        m.add(ms);
        m.add(ely);
        m.add(demo);
        m.addSeparator();
        m.add(sair);
        return m;
    }

    private void dialogoDemo() {
        String n = JOptionPane.showInputDialog(this, "Nome do jogador (3-16 letras, números ou _):", cfgStr("user", "Player"));
        if (n == null) return;
        n = n.trim();
        if (!n.matches("\\w{3,16}")) { aviso(this, "Nome inválido."); return; }
        cfg.put("user", n);
        cfg.put("accType", "demo");
        gravarCfg();
        atualizarConta();
    }

    private void sairConta() {
        for (String k : new String[]{"ms_refresh", "ms_name", "ely_access", "ely_name", "ely_uuid"}) cfg.remove(k);
        cfg.put("accType", "demo");
        sessao = null;
        gravarCfg();
        atualizarConta();
        status("conta desconectada (modo demo).");
    }

    // ---------- ely.by ----------
    private void dialogoEly() {
        JTextField u = new JTextField(20);
        JPasswordField pw = new JPasswordField(20);
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        p.add(new JLabel("Usuário ou e-mail do ely.by:"));
        p.add(u);
        p.add(new JLabel("Senha:"));
        p.add(pw);
        if (JOptionPane.showConfirmDialog(this, p, "Entrar com ely.by", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String user = u.getText().trim();
        String pass = new String(pw.getPassword());
        if (user.isEmpty() || pass.isEmpty()) return;
        new Thread(() -> {
            try {
                status("entrando no ely.by...");
                try {
                    elyLogin(user, pass, null);
                } catch (IOException ex) {
                    if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("two factor")) {
                        String[] cod = new String[1];
                        SwingUtilities.invokeAndWait(() -> cod[0] = JOptionPane.showInputDialog(this,
                                "Código de verificação em duas etapas:"));
                        if (cod[0] == null || cod[0].isBlank()) return;
                        elyLogin(user, pass, cod[0].trim());
                    } else throw ex;
                }
                cfg.put("accType", "elyby");
                gravarCfg();
                atualizarConta();
                status("logado no ely.by como " + cfgStr("ely_name", "?") + ".");
            } catch (Exception ex) {
                status("login falhou: " + ex.getMessage());
                erro("Login ely.by falhou:\n" + ex.getMessage());
            }
        }).start();
    }

    private void elyLogin(String user, String pass, String totp) throws Exception {
        String client = cfgStr("ely_client", UUID.randomUUID().toString());
        String senha = totp == null ? pass : pass + ":" + totp;
        Resp r = http("POST", ELY + "authenticate", Json.write(m("username", user, "password", senha,
                "clientToken", client, "requestUser", true)), JSON_T, null);
        Map<String, Object> mm = map(Json.parse(r.body()));
        if (r.status() != 200) {
            String msg = str(mm.get("errorMessage"));
            throw new IOException(msg != null ? msg : "HTTP " + r.status());
        }
        Map<String, Object> prof = map(mm.get("selectedProfile"));
        cfg.put("ely_access", str(mm.get("accessToken")));
        cfg.put("ely_client", client);
        cfg.put("ely_name", str(prof.get("name")));
        cfg.put("ely_uuid", str(prof.get("id")));
        gravarCfg();
    }

    private Sessao obterEly() throws Exception {
        String acc = str(cfg.get("ely_access")), cli = str(cfg.get("ely_client"));
        if (acc == null) throw new IllegalStateException("Entre com a conta ely.by.");
        Resp v = http("POST", ELY + "validate", Json.write(m("accessToken", acc)), JSON_T, null);
        if (v.status() != 200 && v.status() != 204) {
            Resp r = http("POST", ELY + "refresh", Json.write(m("accessToken", acc, "clientToken", cli)), JSON_T, null);
            if (r.status() != 200) throw new IllegalStateException("Sessão ely.by expirada. Entre de novo.");
            acc = str(map(Json.parse(r.body())).get("accessToken"));
            cfg.put("ely_access", acc);
            gravarCfg();
        }
        return new Sessao(acc, str(cfg.get("ely_uuid")), str(cfg.get("ely_name")), Long.MAX_VALUE);
    }

    /** authlib-injector: faz o jogo falar com o servidor de autenticação do ely.by. */
    private Path garantirAuthlib() throws Exception {
        Path jar = BASE.resolve("authlib-injector.jar");
        if (Files.exists(jar)) return jar;
        status("baixando authlib-injector (ely.by)...");
        Map<String, Object> info = map(Json.parse(getText(AUTHLIB_LATEST)));
        baixar(str(info.get("download_url")), jar, null);
        return jar;
    }

    // ---------- Microsoft ----------
    private void iniciarLoginMicrosoft() {
        btnConta.setEnabled(false);
        new Thread(() -> {
            try {
                loginMicrosoft();
            } catch (Exception ex) {
                status("login falhou: " + ex.getMessage());
                erro("Login falhou:\n" + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnConta.setEnabled(true));
            }
        }).start();
    }

    private void loginMicrosoft() throws Exception {
        String cid = pedirClientId();
        if (cid == null) return;
        cfg.put("clientId", cid);

        Resp r = http("POST", MS_BASE + "devicecode", form("client_id", cid, "scope", MS_SCOPE), FORM, null);
        Map<String, Object> d = map(Json.parse(r.body()));
        if (r.status() != 200) throw new IOException(erroOauth(d));
        String code = str(d.get("user_code"));
        String uri = str(d.get("verification_uri"));
        String dev = str(d.get("device_code"));
        int intervalo = ((Number) d.get("interval")).intValue();
        long fim = System.currentTimeMillis() + ((Number) d.get("expires_in")).longValue() * 1000;

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(code), null);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(uri));
        } catch (Exception ignored) {}
        JDialog dlg = mostrarCodigo(code, uri);
        status("aguardando login no navegador...");

        String acesso = null, refresh = null;
        try {
            while (System.currentTimeMillis() < fim) {
                Thread.sleep(intervalo * 1000L);
                Resp t = http("POST", MS_BASE + "token", form(
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id", cid, "device_code", dev), FORM, null);
                Map<String, Object> tm = map(Json.parse(t.body()));
                if (t.status() == 200) {
                    acesso = str(tm.get("access_token"));
                    refresh = str(tm.get("refresh_token"));
                    break;
                }
                String err = str(tm.get("error"));
                if ("authorization_pending".equals(err)) continue;
                if ("slow_down".equals(err)) { intervalo += 5; continue; }
                throw new IOException(erroOauth(tm));
            }
        } finally {
            SwingUtilities.invokeLater(dlg::dispose);
        }
        if (acesso == null) throw new IOException("Tempo esgotado. Tente de novo.");

        status("autenticando no Xbox / Minecraft...");
        Sessao s = xboxAteMinecraft(acesso);
        sessao = s;
        cfg.put("ms_refresh", refresh);
        cfg.put("ms_name", s.name());
        cfg.put("accType", "microsoft");
        gravarCfg();
        atualizarConta();
        status("logado como " + s.name() + ".");
    }

    private String pedirClientId() throws Exception {
        String atualId = cfgStr("clientId", "");
        if (!atualId.isBlank()) return atualId;
        String[] res = new String[1];
        SwingUtilities.invokeAndWait(() -> res[0] = JOptionPane.showInputDialog(this,
                "<html>O login Microsoft precisa de um <b>Client ID</b> de um app registrado no Azure<br>"
                        + "(tipo 'cliente público', com 'Allow public client flows' ligado)<br>"
                        + "e <b>aprovado pela Mojang</b> para a API do Minecraft<br>"
                        + "(formulário: aka.ms/AppRegistrationForm).<br><br>Cole seu Client ID:</html>",
                "Client ID do Azure", JOptionPane.QUESTION_MESSAGE));
        return res[0] == null || res[0].isBlank() ? null : res[0].trim();
    }

    private JDialog mostrarCodigo(String code, String uri) throws Exception {
        JDialog[] r = new JDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            JDialog dlg = new JDialog(this, "Login Microsoft", false);
            JLabel l = new JLabel("<html><div style='text-align:center'>Abra <b>" + uri + "</b><br>e digite o código:<br><br>"
                    + "<span style='font-size:24px'><b>" + code + "</b></span><br><br>(o código já foi copiado)</div></html>",
                    SwingConstants.CENTER);
            l.setBorder(new EmptyBorder(20, 30, 20, 30));
            dlg.add(l);
            dlg.pack();
            dlg.setLocationRelativeTo(this);
            dlg.setVisible(true);
            r[0] = dlg;
        });
        return r[0];
    }

    /** Token Microsoft -> Xbox Live -> XSTS -> Minecraft -> perfil. */
    private Sessao xboxAteMinecraft(String msAccess) throws Exception {
        Resp x = http("POST", "https://user.auth.xboxlive.com/user/authenticate", Json.write(m(
                "Properties", m("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", "d=" + msAccess),
                "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT")), JSON_T, null);
        if (x.status() != 200) throw new IOException("Xbox Live recusou (HTTP " + x.status() + ").");
        String xbl = str(map(Json.parse(x.body())).get("Token"));

        Resp s = http("POST", "https://xsts.auth.xboxlive.com/xsts/authorize", Json.write(m(
                "Properties", m("SandboxId", "RETAIL", "UserTokens", List.of(xbl)),
                "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT")), JSON_T, null);
        Map<String, Object> sm = map(Json.parse(s.body()));
        if (s.status() != 200) {
            String xerr = str(sm.get("XErr"));
            if ("2148916233".equals(xerr)) throw new IOException("Esta conta Microsoft não tem perfil Xbox. Crie um em xbox.com.");
            if ("2148916238".equals(xerr)) throw new IOException("Conta de menor de idade: precisa estar em uma Família Microsoft.");
            throw new IOException("XSTS recusou (HTTP " + s.status() + ", XErr " + xerr + ").");
        }
        String xsts = str(sm.get("Token"));
        String uhs = str(map(list(map(sm.get("DisplayClaims")).get("xui")).get(0)).get("uhs"));

        Resp mc = http("POST", "https://api.minecraftservices.com/authentication/login_with_xbox",
                Json.write(m("identityToken", "XBL3.0 x=" + uhs + ";" + xsts)), JSON_T, null);
        if (mc.status() != 200)
            throw new IOException("O Minecraft recusou o login (HTTP " + mc.status() + "). Se for 403 'Invalid app registration', "
                    + "o Client ID ainda não foi aprovado pela Mojang (aka.ms/AppRegistrationForm).");
        Map<String, Object> mm = map(Json.parse(mc.body()));
        String token = str(mm.get("access_token"));
        long exp = System.currentTimeMillis() + ((Number) mm.get("expires_in")).longValue() * 1000;

        Resp pr = http("GET", "https://api.minecraftservices.com/minecraft/profile", null, null, "Bearer " + token);
        if (pr.status() == 404) throw new IOException("Esta conta não possui o Minecraft Java Edition.");
        if (pr.status() != 200) throw new IOException("Falha ao ler o perfil (HTTP " + pr.status() + ").");
        Map<String, Object> pm = map(Json.parse(pr.body()));
        return new Sessao(token, str(pm.get("id")), str(pm.get("name")), exp);
    }

    private synchronized Sessao obterSessao() throws Exception {
        if (sessao != null && System.currentTimeMillis() < sessao.expira() - 60_000) return sessao;
        String cid = str(cfg.get("clientId")), ref = str(cfg.get("ms_refresh"));
        if (cid == null || ref == null) throw new IllegalStateException("Entre com a conta Microsoft.");
        Resp t = http("POST", MS_BASE + "token", form("client_id", cid, "grant_type", "refresh_token",
                "refresh_token", ref, "scope", MS_SCOPE), FORM, null);
        Map<String, Object> tm = map(Json.parse(t.body()));
        if (t.status() != 200)
            throw new IllegalStateException("Sessão expirada. Saia da conta e entre de novo (" + str(tm.get("error")) + ").");
        if (tm.get("refresh_token") != null) cfg.put("ms_refresh", str(tm.get("refresh_token")));
        sessao = xboxAteMinecraft(str(tm.get("access_token")));
        gravarCfg();
        return sessao;
    }

    static String erroOauth(Map<String, Object> m) {
        return str(m.get("error")) + ": " + str(m.get("error_description"));
    }

    // ===================== AÇÕES =====================
    private boolean jogoRodando() {
        Process p = jogo;
        return p != null && p.isAlive();
    }

    private void aoJogar() {
        if (jogoRodando()) { // o botão vira "PARAR" enquanto o jogo roda
            pararam = true;
            jogo.destroy();
            status("encerrando o jogo...");
            return;
        }
        if (atual == null) { abrirDialogoInstancia(null); return; }
        Inst in = atual;
        if (!"Vanilla".equals(in.loader) && in.lver.isEmpty()) {
            aviso(this, "Esta instância não tem versão do " + in.loader + ". Clique no lápis e escolha uma.");
            return;
        }
        if ("demo".equals(contaTipo()) && !cfgStr("user", "Player").matches("\\w{3,16}")) {
            aviso(this, "Nome do modo demo inválido. Abra Conta > Modo demo.");
            return;
        }
        salvarConfig();

        btnResolver.setVisible(false);
        erroLauncher = null;
        btnJogar.setEnabled(false);
        btnEditar.setEnabled(false);
        new Thread(() -> {
            boolean direto = "direct".equals(modo);
            boolean falhou = false;
            try {
                int code = instalarEJogar(in);
                falhou = !pararam && (code != 0 || crashNovo(in));
            } catch (Exception ex) {
                ex.printStackTrace();
                erroLauncher = ex.toString();
                falhou = true;
                status("Erro: " + ex.getMessage());
            }
            if (falhou) {
                SwingUtilities.invokeLater(() -> btnResolver.setVisible(true));
                String msg = erroLauncher != null ? "Não consegui iniciar o Minecraft:\n" + erroLauncher : "O Minecraft fechou com erro.";
                Object[] ops = {"RESOLVER", "Fechar"};
                int[] r = {1};
                try {
                    SwingUtilities.invokeAndWait(() -> r[0] = JOptionPane.showOptionDialog(direto ? null : this,
                            msg + "\n\nQuer que o DirectLauncher descubra o problema?", "DirectLauncher",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, ops, ops[0]));
                } catch (Exception ignored) {}
                if (r[0] == 0) {
                    manterAberto = true;
                    SwingUtilities.invokeLater(() -> { setVisible(true); abrirResolver(in); });
                }
            }
            jogo = null;
            if (direto && !manterAberto) System.exit(0);
            SwingUtilities.invokeLater(() -> {
                setState(Frame.NORMAL);
                estiloBotaoJogar(false);
                btnJogar.setEnabled(true);
                btnEditar.setEnabled(atual != null);
            });
        }).start();
    }

    private void estiloBotaoJogar(boolean rodando) {
        btnJogar.setText(rodando ? "PARAR JOGO" : "JOGAR");
        btnJogar.setBackground(rodando ? COR_PARAR : COR_JOGAR);
    }

    private void executarJar() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Arquivos JAR (*.jar)", "jar"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File jar = fc.getSelectedFile();
        try {
            String custom = cfgStr("java", "");
            String exe = Path.of(System.getProperty("java.home"), "bin", OS.equals("windows") ? "java.exe" : "java").toString();
            if (!custom.isEmpty()) {
                Path p = Path.of(custom);
                if (Files.isDirectory(p)) p = p.resolve("bin").resolve(OS.equals("windows") ? "java.exe" : "java");
                if (Files.exists(p)) exe = p.toString();
            }
            Path logs = BASE.resolve("logs");
            Files.createDirectories(logs);
            Path log = logs.resolve(jar.getName() + ".txt");
            ProcessBuilder pb = new ProcessBuilder(exe, "-jar", jar.getName());
            pb.directory(jar.getParentFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(log.toFile());
            pb.start();
            status("executando " + jar.getName() + " (log: " + log.getFileName() + ")");
        } catch (IOException ex) {
            status("erro ao executar o jar: " + ex.getMessage());
            erro("Erro ao executar o jar:\n" + ex.getMessage());
        }
    }

    private Path logAtual() {
        if (atual != null) {
            Path a = BASE.resolve("instances").resolve(atual.dir).resolve("launcher-log.txt");
            if (Files.exists(a)) return a;
            Path b = BASE.resolve("instances").resolve(atual.dir).resolve("logs").resolve("latest.log");
            if (Files.exists(b)) return b;
        }
        return null;
    }

    private void compartilharLog() {
        Path log = logAtual();
        if (log == null) { status("ainda não há registro (log) desta instância. Jogue uma vez primeiro."); return; }
        Object[] ops = {"Copiar texto", "Abrir arquivo", "Cancelar"};
        int r = JOptionPane.showOptionDialog(this,
                "Registro: " + log + "\n\nCopie o texto e cole onde quiser pedir ajuda.",
                "Compartilhar arquivo de registro", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, ops, ops[0]);
        try {
            if (r == 0) {
                List<String> linhas = Arrays.asList(new String(Files.readAllBytes(log), StandardCharsets.UTF_8).split("\\R"));
                List<String> fim = linhas.subList(Math.max(0, linhas.size() - 400), linhas.size());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(String.join("\n", fim)), null);
                status("últimas " + fim.size() + " linhas do registro copiadas.");
            } else if (r == 1 && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(log.toFile());
            }
        } catch (IOException ex) {
            status("erro ao ler o registro: " + ex.getMessage());
        }
    }

    private void abrirDiretorioDoJogo() {
        abrir(atual == null ? BASE : BASE.resolve("instances").resolve(atual.dir));
    }

    private void verificarBibliotecas(Inst in) {
        if (in == null) { status("selecione uma instância."); return; }
        status("verificando arquivos de " + in.name + "...");
        new Thread(() -> {
            try {
                String r = verificarEReparar(in);
                status(r.split("\n")[0]);
                SwingUtilities.invokeLater(() -> mostrarTexto(this, "Verificação", r));
            } catch (Exception ex) {
                status("erro na verificação: " + ex.getMessage());
            }
        }).start();
    }

    // ===================== MANIFESTO / VERSÕES =====================
    private void carregarManifesto() { carregarManifesto(null); }

    private void carregarManifesto(Runnable depois) {
        status("carregando versões...");
        new Thread(() -> {
            try {
                Path cache = BASE.resolve("version_manifest_v2.json");
                String txt;
                try {
                    txt = getText(MANIFEST);
                    Files.writeString(cache, txt);
                } catch (Exception e) {
                    if (Files.exists(cache)) txt = Files.readString(cache); // modo offline
                    else throw e;
                }
                Map<String, Object> mm = map(Json.parse(txt));
                List<Ver> lista = new ArrayList<>();
                for (Object o : list(mm.get("versions"))) {
                    Map<String, Object> v = map(o);
                    lista.add(new Ver(str(v.get("id")), str(v.get("type")), str(v.get("url"))));
                }
                SwingUtilities.invokeLater(() -> {
                    todas.clear();
                    todas.addAll(lista);
                    status("pronto (" + lista.size() + " versões disponíveis).");
                    if (depois != null) depois.run();
                });
            } catch (Exception e) {
                status("erro ao carregar versões: " + e.getMessage());
                if (depois != null) SwingUtilities.invokeLater(depois); // tenta mesmo assim (versões já baixadas funcionam offline)
            }
        }).start();
    }

    // ===================== VERSÕES DO LOADER (Fabric / Forge) =====================
    private List<LV> fabricVersoes(String mc) throws Exception {
        List<Object> arr = list(Json.parse(getText("https://meta.fabricmc.net/v2/versions/loader/" + mc)));
        List<LV> out = new ArrayList<>();
        boolean primeiro = true;
        for (Object o : arr) {
            Map<String, Object> l = map(map(o).get("loader"));
            String ver = str(l.get("version"));
            boolean estavel = Boolean.TRUE.equals(l.get("stable"));
            out.add(new LV(ver, ver + (estavel ? "" : " (beta)") + (primeiro ? "  (latest)" : "")));
            primeiro = false;
        }
        return out;
    }

    /** Formato Forge: 1.7.10-10.13.4.1614-1.7.10 = [Minecraft]-[Forge]-[build extra]. */
    private List<LV> forgeVersoes(String mc) throws Exception {
        synchronized (Main.class) {
            if (forgeMeta == null) {
                String xml = getText(FORGE_META);
                List<String> l = new ArrayList<>();
                Matcher mt = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
                while (mt.find()) l.add(mt.group(1));
                forgeMeta = l;
            }
        }
        String rec = null, lat = null;
        try {
            Map<String, Object> promos = map(map(Json.parse(getText(FORGE_PROMOS))).get("promos"));
            rec = str(promos.get(mc + "-recommended"));
            lat = str(promos.get(mc + "-latest"));
        } catch (Exception ignored) {}
        List<LV> out = new ArrayList<>();
        for (int i = forgeMeta.size() - 1; i >= 0; i--) {
            String full = forgeMeta.get(i);
            if (!full.startsWith(mc + "-")) continue;
            String b = forgeBuild(mc, full);
            String tag = b.equals(rec) ? "  (recommended)" : b.equals(lat) ? "  (latest)" : "";
            out.add(new LV(full, b + tag));
        }
        return out;
    }

    static String forgeBuild(String mc, String full) {
        String b = full.substring(mc.length() + 1);
        int k = b.indexOf('-');
        return k > 0 ? b.substring(0, k) : b;
    }

    // ===================== INSTALAR E JOGAR =====================
    private int instalarEJogar(Inst in) throws Exception {
        String loader = in.loader;
        int ram = in.ram > 0 ? in.ram : cfgInt("ram", 2048);
        String javaCustom = !in.java.isBlank() ? in.java : cfgStr("java", "");
        Path libs = BASE.resolve("libraries");
        Path aroot = BASE.resolve("assets");
        Path gameDir = BASE.resolve("instances").resolve(in.dir);
        Files.createDirectories(gameDir.resolve("mods"));

        // 1) Metadados da versão base + Java
        status("baixando metadados de " + in.mc + "...");
        Map<String, Object> van = lerVersao(in.mc);
        int need = 8; // versões <= 1.16 pedem Java 8; ausente no JSON = Java 8
        if (van.get("javaVersion") != null)
            need = ((Number) map(van.get("javaVersion")).get("majorVersion")).intValue();
        status("procurando Java " + need + "...");
        String javaExe = escolherJava(need, javaCustom);
        int gpu = cfgInt("gpu", 0);
        if (gpu != 0 && OS.equals("windows")) {
            regGpu(javaExe, gpu);
            regGpu(javaExe.replace("java.exe", "javaw.exe"), gpu);
        }

        // 2) Jogo base (o instalador do Forge precisa dele)
        Path vdirBase = BASE.resolve("versions").resolve(in.mc);
        Map<String, Object> clienteBase = map(map(van.get("downloads")).get("client"));
        Path jarBase = vdirBase.resolve(in.mc + ".jar");
        status("baixando o jogo base...");
        baixar(str(clienteBase.get("url")), jarBase, str(clienteBase.get("sha1")));

        // 3) Loader
        String launchId = in.mc;
        if ("Fabric".equals(loader)) launchId = instalarFabric(in.mc, in.lver);
        else if ("Forge".equals(loader)) launchId = instalarForge(in.mc, in.lver, javaExe);

        Map<String, Object> j = carregarMesclado(launchId);
        String vanillaId = j.get("_vanilla") != null ? str(j.get("_vanilla")) : launchId;
        Path vdir = BASE.resolve("versions").resolve(vanillaId);

        // 4) Lista de downloads (libs)
        List<Dl> dls = new ArrayList<>();
        List<Path> classpath = new ArrayList<>();
        List<Path> nativeZips = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (Object lo : list(j.get("libraries"))) {
            Map<String, Object> lib = map(lo);
            if (!rulesAllow(lib.get("rules"))) continue;
            String nome = str(lib.get("name"));
            if (nome != null) {
                String[] pp = nome.split(":");
                String chave = pp.length >= 2 ? pp[0] + ":" + pp[1] + (pp.length > 3 ? ":" + pp[3] : "") : nome;
                if (!vistos.add(chave)) continue; // o do loader vem primeiro e vence
            }
            if (lib.get("downloads") == null) { // estilo Maven (Fabric / Forge antigo)
                if (nome == null) continue;
                String rel = mavenPath(nome);
                String base = lib.get("url") != null ? str(lib.get("url")) : "https://libraries.minecraft.net/";
                base = base.replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
                        .replace("https://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/");
                if (!base.endsWith("/")) base += "/";
                Path p = libs.resolve(rel);
                dls.add(new Dl(base + rel, p, null));
                classpath.add(p);
                continue;
            }
            Map<String, Object> d = map(lib.get("downloads"));
            if (d.get("artifact") != null) {
                Map<String, Object> a = map(d.get("artifact"));
                Path p = libs.resolve(str(a.get("path")));
                dls.add(new Dl(str(a.get("url")), p, str(a.get("sha1"))));
                classpath.add(p);
            }
            if (lib.get("natives") != null && d.get("classifiers") != null) {
                Object cl = map(lib.get("natives")).get(OS);
                if (cl != null) {
                    String classif = str(cl).replace("${arch}", IS64 ? "64" : "32");
                    Object co = map(d.get("classifiers")).get(classif);
                    if (co != null) {
                        Map<String, Object> a = map(co);
                        Path p = libs.resolve(str(a.get("path")));
                        dls.add(new Dl(str(a.get("url")), p, str(a.get("sha1"))));
                        nativeZips.add(p);
                    }
                }
            }
        }
        classpath.add(jarBase);

        // 5) Assets
        Map<String, Object> ai = map(j.get("assetIndex"));
        String assetId = str(ai.get("id"));
        Path idx = aroot.resolve("indexes").resolve(assetId + ".json");
        status("baixando índice de assets...");
        baixar(str(ai.get("url")), idx, str(ai.get("sha1")));
        Map<String, Object> idxm = map(Json.parse(Files.readString(idx)));
        Map<String, Object> objs = map(idxm.get("objects"));
        for (Map.Entry<String, Object> e : objs.entrySet()) {
            String h = str(map(e.getValue()).get("hash"));
            String pre = h.substring(0, 2);
            dls.add(new Dl(ASSETS_URL + pre + "/" + h, aroot.resolve("objects").resolve(pre).resolve(h), h));
        }

        // 6) Baixar tudo em paralelo (sem destinos repetidos)
        Set<Path> destinos = new HashSet<>();
        dls.removeIf(d -> !destinos.add(d.dest()));
        status("baixando " + dls.size() + " arquivos (só os que faltam)...");
        progresso(0, dls.size());
        baixarTudo(dls);

        // 7) Natives
        Path nat = vdir.resolve("natives").toAbsolutePath().normalize();
        for (Path z : nativeZips) unzip(z, nat);

        // 8) Assets legados (versões antigas)
        Path gameAssets = aroot;
        boolean virt = Boolean.TRUE.equals(idxm.get("virtual"));
        boolean mapRes = Boolean.TRUE.equals(idxm.get("map_to_resources"));
        if (virt || mapRes) {
            Path alvo = virt ? aroot.resolve("virtual").resolve("legacy") : gameDir.resolve("resources");
            for (Map.Entry<String, Object> e : objs.entrySet()) {
                String h = str(map(e.getValue()).get("hash"));
                Path src = aroot.resolve("objects").resolve(h.substring(0, 2)).resolve(h);
                Path dst = alvo.resolve(e.getKey()).normalize();
                if (!dst.startsWith(alvo) || Files.exists(dst)) continue;
                Files.createDirectories(dst.getParent());
                Files.copy(src, dst);
            }
            gameAssets = alvo;
        }

        // 9) Conta (Microsoft, ely.by ou demo)
        String user, uuid, token, userType;
        boolean demo = false;
        String agente = null;
        switch (contaTipo()) {
            case "microsoft" -> {
                status("renovando sessão Microsoft...");
                Sessao s = obterSessao();
                user = s.name(); uuid = s.uuid(); token = s.token(); userType = "msa";
            }
            case "elyby" -> {
                status("renovando sessão ely.by...");
                Sessao s = obterEly();
                user = s.name(); uuid = s.uuid(); token = s.token(); userType = "mojang";
                agente = "-javaagent:" + garantirAuthlib().toAbsolutePath() + "=ely.by";
            }
            default -> {
                user = cfgStr("user", "Player");
                uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8))
                        .toString().replace("-", "");
                token = "0"; userType = "legacy"; demo = true;
            }
        }

        // 10) Opções do jogo (escala da interface e idioma)
        String gs = cfgStr("guiScale", "0");
        if (gs.matches("[1-4]")) definirOpcao(gameDir, "guiScale", gs);
        if (cfgBool("forceEn", false)) definirOpcao(gameDir, "lang", "en_us");

        // 11) Montar comando
        StringBuilder cp = new StringBuilder();
        for (Path p : classpath) {
            if (cp.length() > 0) cp.append(File.pathSeparator);
            cp.append(p.toAbsolutePath().normalize());
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", user);
        vars.put("version_name", vanillaId);
        vars.put("game_directory", gameDir.toAbsolutePath().toString());
        vars.put("assets_root", aroot.toAbsolutePath().toString());
        vars.put("game_assets", gameAssets.toAbsolutePath().toString());
        vars.put("assets_index_name", assetId);
        vars.put("auth_uuid", uuid);
        vars.put("auth_access_token", token);
        vars.put("auth_session", token);
        vars.put("user_type", userType);
        vars.put("user_properties", "{}");
        vars.put("version_type", str(j.get("type")));
        vars.put("natives_directory", nat.toString());
        vars.put("launcher_name", "DirectLauncher");
        vars.put("launcher_version", "3.0");
        vars.put("classpath", cp.toString());
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", libs.toAbsolutePath().toString());
        vars.put("clientid", "");
        vars.put("auth_xuid", "");

        List<String> jvm, game;
        if (j.get("arguments") != null) {
            Map<String, Object> a = map(j.get("arguments"));
            jvm = flatten(a.get("jvm"));
            game = flatten(a.get("game"));
        } else {
            game = new ArrayList<>(Arrays.asList(str(j.get("minecraftArguments")).split(" ")));
            jvm = new ArrayList<>(List.of("-Djava.library.path=${natives_directory}", "-cp", "${classpath}"));
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Xmx" + ram + "M");
        if (agente != null) cmd.add(agente);
        for (String extra : (cfgStr("jvmArgs", "") + " " + in.jvm).trim().split("\\s+"))
            if (!extra.isEmpty()) cmd.add(extra);
        for (String s : jvm) cmd.add(sub(s, vars));
        cmd.add(str(j.get("mainClass")));
        boolean temToken = false, temOpcoes = false;
        for (String s : game) {
            if (s.contains("accessToken")) temToken = true;
            if (s.startsWith("--")) temOpcoes = true;
            cmd.add(sub(s, vars));
        }
        if (temOpcoes) { // versões antigas tipo Alpha usam argumentos posicionais
            int w = cfgInt("width", 0), h = cfgInt("height", 0);
            if (w > 0 && h > 0) { cmd.add("--width"); cmd.add(String.valueOf(w)); cmd.add("--height"); cmd.add(String.valueOf(h)); }
            if (cfgBool("fullscreen", false)) cmd.add("--fullscreen");
        }
        if (demo && temToken) cmd.add("--demo"); // modo demo oficial (sem conta)

        // 12) Iniciar
        status("iniciando " + in.name + "...");
        Path log = gameDir.resolve("launcher-log.txt");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        pararam = false;
        inicioJogo = System.currentTimeMillis();
        Process p = pb.start();
        jogo = p;
        iniciarBackupAuto(in);
        boolean minimizar = cfgBool("minimize", false);
        SwingUtilities.invokeLater(() -> {
            if (splash != null) { splash.dispose(); splash = null; }
            estiloBotaoJogar(true);
            btnJogar.setEnabled(true);
            if (minimizar) setState(Frame.ICONIFIED);
        });
        progresso(1, 1);
        status(in.name + " em execução (log: " + log.getFileName() + ")");

        int code;
        try {
            code = p.waitFor();
        } finally {
            pararBackupAuto();
        }
        if (cfgBool("autoBackup", true)) {
            try {
                backupAuto(in, Math.max(1, cfgInt("autoManter", 5)));
            } catch (Exception e) {
                status("backup ao encerrar falhou: " + e.getMessage());
            }
        }
        status(pararam || code == 0 ? "jogo encerrado." : "jogo encerrou com código " + code + " - veja o registro (log).");
        return code;
    }

    static void definirOpcao(Path gameDir, String chave, String valor) {
        try {
            Path f = gameDir.resolve("options.txt");
            List<String> l = Files.exists(f) ? new ArrayList<>(Files.readAllLines(f)) : new ArrayList<>();
            boolean achou = false;
            for (int i = 0; i < l.size(); i++)
                if (l.get(i).startsWith(chave + ":")) { l.set(i, chave + ":" + valor); achou = true; }
            if (!achou) l.add(chave + ":" + valor);
            Files.write(f, l);
        } catch (IOException ignored) {}
    }

    // ===================== VERSÕES (JSON) =====================
    private Map<String, Object> lerVersao(String id) throws Exception {
        Path f = BASE.resolve("versions").resolve(id).resolve(id + ".json");
        if (!Files.exists(f)) {
            Ver ver = null;
            for (Ver x : todas) if (x.id().equals(id)) ver = x;
            if (ver == null) throw new IOException("Versão base não encontrada: " + id);
            Files.createDirectories(f.getParent());
            Files.writeString(f, getText(ver.url()));
        }
        return map(Json.parse(Files.readString(f)));
    }

    /** Resolve "inheritsFrom" (Fabric/Forge): libs do loader primeiro, depois as do jogo base. */
    private Map<String, Object> carregarMesclado(String id) throws Exception {
        Map<String, Object> c = lerVersao(id);
        Object pai = c.get("inheritsFrom");
        if (pai == null) return c;
        Map<String, Object> p = carregarMesclado(str(pai));
        Map<String, Object> r = new LinkedHashMap<>(p);
        for (Map.Entry<String, Object> e : c.entrySet()) {
            switch (e.getKey()) {
                case "libraries" -> {
                    List<Object> l = new ArrayList<>(list(e.getValue()));
                    if (p.get("libraries") != null) l.addAll(list(p.get("libraries")));
                    r.put("libraries", l);
                }
                case "arguments" -> {
                    Map<String, Object> pa = p.get("arguments") == null ? new HashMap<>() : map(p.get("arguments"));
                    Map<String, Object> ca = map(e.getValue());
                    Map<String, Object> a = new LinkedHashMap<>();
                    for (String k : List.of("game", "jvm")) {
                        List<Object> l = new ArrayList<>();
                        if (pa.get(k) != null) l.addAll(list(pa.get(k)));
                        if (ca.get(k) != null) l.addAll(list(ca.get(k)));
                        a.put(k, l);
                    }
                    r.put("arguments", a);
                }
                case "inheritsFrom" -> { }
                default -> r.put(e.getKey(), e.getValue());
            }
        }
        r.put("_vanilla", p.get("_vanilla") != null ? p.get("_vanilla") : str(pai));
        return r;
    }

    private String instalarFabric(String mc, String loaderVer) throws Exception {
        status("baixando perfil do Fabric " + loaderVer + "...");
        String txt = getText("https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loaderVer + "/profile/json");
        String id = str(map(Json.parse(txt)).get("id"));
        Path dir = BASE.resolve("versions").resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), txt);
        return id;
    }

    private String instalarForge(String mc, String full, String javaExe) throws Exception {
        String build = forgeBuild(mc, full);
        String ja = acharForge(build);
        if (ja != null) return ja;

        Path inst = BASE.resolve("installers").resolve("forge-" + full + "-installer.jar");
        status("baixando instalador do Forge " + build + "...");
        baixar("https://maven.minecraftforge.net/net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar", inst, null);

        // Instaladores antigos (até 1.12.2): install_profile.json traz "versionInfo" -> instalação direta
        try (ZipFile z = new ZipFile(inst.toFile())) {
            ZipEntry pe = z.getEntry("install_profile.json");
            if (pe != null) {
                Map<String, Object> ip = map(Json.parse(new String(z.getInputStream(pe).readAllBytes(), StandardCharsets.UTF_8)));
                if (ip.get("versionInfo") != null) {
                    Map<String, Object> vi = map(ip.get("versionInfo"));
                    vi.putIfAbsent("inheritsFrom", mc);
                    String id = str(vi.get("id"));
                    Path dir = BASE.resolve("versions").resolve(id);
                    Files.createDirectories(dir);
                    Files.writeString(dir.resolve(id + ".json"), Json.write(vi));
                    Map<String, Object> inn = map(ip.get("install"));
                    Path out = BASE.resolve("libraries").resolve(mavenPath(str(inn.get("path"))));
                    Files.createDirectories(out.getParent());
                    try (InputStream is = z.getInputStream(z.getEntry(str(inn.get("filePath"))))) {
                        Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return id;
                }
            }
        }

        // Instaladores novos (1.13+): executar o instalador em modo cliente
        Path lp = BASE.resolve("launcher_profiles.json");
        if (!Files.exists(lp)) Files.writeString(lp, "{\"profiles\":{},\"version\":3}");
        status("instalando Forge (pode levar alguns minutos)...");
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-jar", inst.toAbsolutePath().toString(),
                "--installClient", BASE.toAbsolutePath().toString());
        pb.directory(BASE.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(BASE.resolve("forge-installer-log.txt").toFile());
        int code = pb.start().waitFor();
        String id = acharForge(build);
        if (code != 0 || id == null)
            throw new IOException("O instalador do Forge falhou (código " + code + "). Veja forge-installer-log.txt");
        return id;
    }

    private String acharForge(String build) throws IOException {
        Path vs = BASE.resolve("versions");
        if (!Files.isDirectory(vs)) return null;
        try (var s = Files.list(vs)) {
            for (Path d : (Iterable<Path>) s::iterator) {
                String n = d.getFileName().toString();
                if (n.contains(build) && n.toLowerCase().contains("forge") && Files.exists(d.resolve(n + ".json"))) return n;
            }
        }
        return null;
    }

    static String mavenPath(String name) {
        String ext = "jar";
        int at = name.indexOf('@');
        if (at > 0) { ext = name.substring(at + 1); name = name.substring(0, at); }
        String[] p = name.split(":");
        String c = p.length > 3 ? "-" + p[3] : "";
        return p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + c + "." + ext;
    }

    // ===================== REGRAS / ARGUMENTOS =====================
    static boolean rulesAllow(Object rulesObj) {
        if (rulesObj == null) return true;
        boolean allowed = false;
        for (Object ro : list(rulesObj)) {
            Map<String, Object> r = map(ro);
            boolean applies = true;
            if (r.get("os") != null) {
                Map<String, Object> os = map(r.get("os"));
                if (os.get("name") != null && !OS.equals(os.get("name"))) applies = false;
                Object arch = os.get("arch");
                if ("x86".equals(arch) && IS64) applies = false;
                if ("arm64".equals(arch) && !IS_ARM) applies = false;
            }
            if (r.get("features") != null) applies = false; // sem demo/resolução custom
            if (applies) allowed = "allow".equals(r.get("action"));
        }
        return allowed;
    }

    static List<String> flatten(Object arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (Object o : list(arr)) {
            if (o instanceof String s) {
                out.add(s);
            } else {
                Map<String, Object> mm = map(o);
                if (!rulesAllow(mm.get("rules"))) continue;
                Object val = mm.get("value");
                if (val instanceof String t) out.add(t);
                else if (val != null) for (Object x : list(val)) out.add(str(x));
            }
        }
        return out;
    }

    static String sub(String s, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet())
            s = s.replace("${" + e.getKey() + "}", e.getValue());
        return s;
    }

    // ===================== JAVA (AUTO) =====================
    /** Escolhe o java.exe: campo manual > JVM atual > instalados > download (Temurin). */
    private String escolherJava(int need, String custom) throws Exception {
        if (!custom.isEmpty()) {
            Path p = Path.of(custom);
            if (Files.isDirectory(p)) p = Path.of(custom, "bin", OS.equals("windows") ? "java.exe" : "java");
            if (!Files.exists(p)) throw new IllegalStateException("Java não encontrado em: " + custom);
            return p.toString();
        }
        boolean exato8 = need <= 8;
        int atual = Runtime.version().feature();
        if (exato8 ? atual == 8 : atual >= need)
            return Path.of(System.getProperty("java.home"), "bin", OS.equals("windows") ? "java.exe" : "java").toString();

        for (Path h : javasInstalados()) {
            int mj = majorDe(h);
            if (exato8 ? mj == 8 : mj >= need) return javaExeEm(h).toString();
        }
        return baixarJava(exato8 ? 8 : Math.max(need, 17));
    }

    private List<Path> javasInstalados() {
        List<Path> homes = new ArrayList<>();
        String home = System.getProperty("user.home");
        coletar(BASE.resolve("runtime"), 3, homes);
        coletar(Path.of(home, ".jdks"), 3, homes);
        if (System.getenv("JAVA_HOME") != null) coletar(Path.of(System.getenv("JAVA_HOME")), 0, homes);
        if (OS.equals("windows")) {
            for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)"}) {
                String pf = System.getenv(env);
                if (pf == null) continue;
                for (String sub : new String[]{"Java", "Eclipse Adoptium", "Microsoft", "Zulu",
                        "Amazon Corretto", "BellSoft", "Semeru", "AdoptOpenJDK"})
                    coletar(Path.of(pf, sub), 3, homes);
            }
        } else {
            coletar(Path.of("/usr/lib/jvm"), 3, homes);
            coletar(Path.of("/Library/Java/JavaVirtualMachines"), 3, homes);
        }
        return homes;
    }

    static Path javaExeEm(Path home) {
        Path e = home.resolve("bin").resolve(OS.equals("windows") ? "java.exe" : "java");
        return Files.exists(e) ? e : null;
    }

    static void coletar(Path dir, int prof, List<Path> out) {
        if (!Files.isDirectory(dir)) return;
        if (javaExeEm(dir) != null) { out.add(dir); return; }
        if (prof == 0) return;
        try (var s = Files.list(dir)) {
            for (Path c : (Iterable<Path>) s::iterator) coletar(c, prof - 1, out);
        } catch (IOException ignored) {}
    }

    /** Lê o arquivo "release" do JDK/JRE: 1.8.0_xxx -> 8, 17.0.2 -> 17. */
    static int majorDe(Path home) {
        try {
            for (String l : Files.readAllLines(home.resolve("release"))) {
                if (l.startsWith("JAVA_VERSION=")) {
                    String v = l.substring(13).replace("\"", "").trim();
                    String[] p = v.split("[._\\-+]");
                    int a = Integer.parseInt(p[0]);
                    return a == 1 ? Integer.parseInt(p[1]) : a;
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String baixarJava(int major) throws Exception {
        if (!OS.equals("windows"))
            throw new IllegalStateException("Instale o Java " + major + " e informe o caminho em Configuração > Ajustes do Java.");
        final boolean[] sim = {false};
        SwingUtilities.invokeAndWait(() -> sim[0] = JOptionPane.showConfirmDialog(this,
                "Esta versão precisa do Java " + major + ", que não foi encontrado.\n"
                        + "Baixar automaticamente (Eclipse Temurin, ~50 MB)?",
                "Java necessário", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
        if (!sim[0])
            throw new IllegalStateException("Java " + major + " necessário. Informe o caminho em Configuração > Ajustes do Java.");

        String url = "https://api.adoptium.net/v3/binary/latest/" + major + "/ga/windows/"
                + (IS64 ? "x64" : "x86") + "/jre/hotspot/normal/eclipse";
        Path dir = BASE.resolve("runtime").resolve("java" + major).toAbsolutePath().normalize();
        Path zip = dir.resolve("java" + major + ".zip");
        Files.createDirectories(dir);
        status("baixando Java " + major + " (Temurin)...");
        baixar(url, zip, null);
        status("extraindo Java " + major + "...");
        unzip(zip, dir);
        Files.deleteIfExists(zip);

        List<Path> homes = new ArrayList<>();
        coletar(dir, 3, homes);
        if (homes.isEmpty()) throw new IOException("Falha ao instalar o Java " + major);
        return javaExeEm(homes.get(0)).toString();
    }

    // ===================== DOWNLOAD / HTTP =====================
    private void baixarTudo(List<Dl> dls) throws Exception {
        AtomicInteger feitos = new AtomicInteger();
        int total = dls.size();
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(1, Math.min(16, cfgInt("threads", 8))));
        try {
            List<Future<Void>> fs = new ArrayList<>();
            for (Dl d : dls) {
                fs.add(ex.submit(() -> {
                    baixar(d.url(), d.dest(), d.sha1());
                    int n = feitos.incrementAndGet();
                    if (n % 5 == 0 || n == total) progresso(n, total);
                    return null;
                }));
            }
            for (Future<Void> f : fs) f.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception ce) throw ce;
            throw e;
        } finally {
            ex.shutdownNow();
        }
    }

    /** Baixa só se o arquivo não existir; valida SHA1; grava via .part. */
    static void baixar(String url, Path dest, String sha1) throws Exception {
        if (Files.exists(dest)) return;
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).build(),
                HttpResponse.BodyHandlers.ofFile(tmp));
        if (r.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + r.statusCode() + " em " + url);
        }
        if (sha1 != null && !sha1.equalsIgnoreCase(sha1(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException("SHA1 inválido em " + url);
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    static String getText(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " em " + url);
        return r.body();
    }

    static Resp http(String method, String url, String body, String ctype, String auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json").header("User-Agent", UA);
        if (ctype != null) b.header("Content-Type", ctype);
        if (auth != null) b.header("Authorization", auth);
        b.method(method, body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(r.statusCode(), r.body());
    }

    static String form(String... kv) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) b.append('&');
            b.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        return b.toString();
    }

    static String sha1(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void unzip(Path zip, Path dir) throws IOException {
        Files.createDirectories(dir);
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory() || e.getName().startsWith("META-INF")) continue;
                Path out = dir.resolve(e.getName()).normalize();
                if (!out.startsWith(dir)) continue; // proteção zip-slip
                Files.createDirectories(out.getParent());
                Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ===================== RESOLVER PROBLEMAS =====================
    interface Acao { String run() throws Exception; }

    record Achado(String id, String titulo, String detalhe, String botao, Acao acao) {}

    private volatile String erroLauncher;
    private volatile long inicioJogo;
    private volatile boolean manterAberto;

    static final String JVM_OTIM = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 "
            + "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=20";
    static final String MODRINTH = "https://api.modrinth.com/v2/";
    static final List<List<String>> FABRIC_OTIM = List.of(List.of("sodium"), List.of("lithium"),
            List.of("ferrite-core"), List.of("modernfix"), List.of("entityculling"), List.of("immediatelyfast"));
    static final List<List<String>> FORGE_OTIM = List.of(List.of("embeddium", "rubidium"),
            List.of("ferrite-core"), List.of("modernfix"), List.of("entityculling"));

    private Path pastaDoJogo(Inst in) { return BASE.resolve("instances").resolve(in.dir); }

    private int ramEfetiva(Inst in) { return in.ram > 0 ? in.ram : cfgInt("ram", 2048); }

    static long memFisicaMB() {
        try {
            var os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return os.getTotalMemorySize() / (1024 * 1024);
        } catch (Throwable t) {
            return 0;
        }
    }

    static boolean tem(String low, String... chaves) {
        for (String c : chaves) if (low.contains(c)) return true;
        return false;
    }

    /** Procura nos registros (em minúsculas) as causas conhecidas e devolve os ids encontrados. */
    static Set<String> detectarCausas(String low) {
        Set<String> s = new LinkedHashSet<>();
        if (tem(low, "unsupportedclassversionerror", "more recent version of the java runtime", "unsupported major.minor",
                "cannot be cast to class java.net.urlclassloader", "java não encontrado em", "java necessário")) s.add("java");
        if (tem(low, "could not reserve enough space", "invalid maximum heap size", "error occurred during initialization of vm",
                "too small initial heap", "could not create the java virtual machine")) s.add("ram-menos");
        if (tem(low, "java.lang.outofmemoryerror", "gc overhead limit exceeded")) s.add("ram-mais");
        if (tem(low, "unsatisfiedlinkerror", "failed to locate library", "failed to load library", "in java.library.path")) s.add("natives");
        if (tem(low, "zipexception", "zip end header not found", "invalid loc header", "invalid cen header",
                "invalid or corrupt jarfile", "error in opening zip file", "sha1 inválido", "checksum mismatch",
                "noclassdeffounderror: net/minecraft", "classnotfoundexception: net.minecraft")) s.add("corrompido");
        if (tem(low, "could not find or load main class", "o instalador do forge falhou", "forge-installer",
                "unable to launch", "failed to load main class")) s.add("loader");
        if (tem(low, "mod loading has failed", "modloadingexception", "incompatible mod set", "missing or unsupported mandatory dependencies",
                "mixin apply failed", "mixintransformererror", "mixinapplyerror", "duplicatemodsfoundexception", "failed to load mod",
                "loading errors encountered", "mod resolution failed", "nosuchmethoderror", "nosuchfielderror",
                "could not execute entrypoint", "dependency resolution failed")) s.add("mods");
        if (tem(low, "jsonsyntaxexception", "malformedjsonexception", "failed to parse config", "configloadexception",
                "tomlparseexception", "expected begin_object", "com.electronwill.nightconfig")) s.add("config");
        if (tem(low, "failed to load options", "could not read options.txt", "error reading options")) s.add("options");
        if (tem(low, "glfw error", "pixel format not accelerated", "could not create context", "driver does not appear to support opengl",
                "failed to create the glfw window", "failed to initialize glfw", "opengl error", "vulkan error")) s.add("video");
        if (tem(low, "unknownhostexception", "connect timed out", "sockettimeoutexception", "connectexception", "unresolvedaddressexception",
                "httpconnecttimeoutexception", "sslhandshakeexception", "unable to tunnel", "http 500", "http 502", "http 503")) s.add("rede");
        if (tem(low, "http 404 em", "http 403 em")) s.add("http404");
        if (tem(low, "invalid session", "failed to verify username", "sessão expirada", "entre com a conta", "invalid app registration")) s.add("sessao");
        return s;
    }

    /** Últimas linhas que parecem erro (Caused by, Exception, Error). */
    static List<String> linhasChave(String txt) {
        List<String> res = new ArrayList<>();
        for (String l : txt.split("\\R")) {
            String t = l.trim();
            boolean info = t.contains("/INFO]") || t.contains("/WARN]") || t.contains("/DEBUG]");
            boolean erro = t.startsWith("Caused by:") || t.contains("Exception in thread")
                    || (!info && !t.startsWith("at ") && (t.contains("Exception:") || t.contains("Error:") || t.contains("/ERROR]")));
            if (!erro) continue;
            if (t.length() > 200) t = t.substring(0, 200) + "...";
            res.remove(t);
            res.add(t);
        }
        return new ArrayList<>(res.subList(Math.max(0, res.size() - 4), res.size()));
    }

    static List<Path> suspeitos(String txt, List<Path> jars) {
        String low = txt.toLowerCase();
        List<Path> res = new ArrayList<>();
        for (Path j : jars) if (low.contains(j.getFileName().toString().toLowerCase())) res.add(j);
        return res;
    }

    static List<Path> jarsEm(Path dir) {
        List<Path> l = new ArrayList<>();
        if (!Files.isDirectory(dir)) return l;
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar")).sorted().forEach(l::add);
        } catch (IOException ignored) {}
        return l;
    }

    static String ultimas(Path f, int n) {
        for (var cs : new java.nio.charset.Charset[]{StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1}) {
            try {
                List<String> l = Files.readAllLines(f, cs);
                return String.join("\n", l.subList(Math.max(0, l.size() - n), l.size()));
            } catch (IOException ignored) {}
        }
        return "";
    }

    private Path crashRecente(Inst in) {
        Path dir = pastaDoJogo(in).resolve("crash-reports");
        if (!Files.isDirectory(dir)) return null;
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean crashNovo(Inst in) {
        Path c = crashRecente(in);
        return c != null && inicioJogo > 0 && c.toFile().lastModified() >= inicioJogo - 2000;
    }

    private String coletarRegistros(Inst in) {
        StringBuilder b = new StringBuilder();
        if (erroLauncher != null) b.append("[launcher] ").append(erroLauncher).append('\n');
        Path g = pastaDoJogo(in);
        Path fl = BASE.resolve("forge-installer-log.txt");
        if (erroLauncher != null && erroLauncher.contains("Forge") && Files.exists(fl)) b.append(ultimas(fl, 80)).append('\n');
        Path l1 = g.resolve("launcher-log.txt");
        if (Files.exists(l1)) b.append(ultimas(l1, 600)).append('\n');
        Path c = crashRecente(in);
        if (c != null) b.append(ultimas(c, 200)).append('\n');
        Path l2 = g.resolve("logs").resolve("latest.log");
        if (Files.exists(l2)) b.append(ultimas(l2, 300)).append('\n');
        return b.toString();
    }

    private static void adicionar(Map<String, Achado> m, Achado a) { m.putIfAbsent(a.id(), a); }

    private List<Achado> diagnosticar(Inst in) {
        Map<String, Achado> m = new LinkedHashMap<>();
        String txt = coletarRegistros(in);
        Set<String> causas = detectarCausas(txt.toLowerCase());
        Path g = pastaDoJogo(in);
        List<Path> mods = jarsEm(g.resolve("mods"));
        List<Path> off = jarsEm(g.resolve("mods_desativados"));
        int ramAtual = ramEfetiva(in);
        long fis = memFisicaMB();

        Path cr = crashRecente(in);
        if (cr != null && crashNovo(in)) {
            String desc = "";
            for (String l : ultimas(cr, 40).split("\\R")) if (l.startsWith("Description:")) { desc = l.substring(12).trim(); break; }
            adicionar(m, new Achado("crash", "O jogo gerou um relatório de crash",
                    (desc.isEmpty() ? "" : desc + ". ") + "Arquivo: " + cr.getFileName(), "ABRIR RELATÓRIO",
                    () -> { Desktop.getDesktop().open(cr.toFile()); return "Relatório aberto."; }));
        }
        if (causas.contains("java"))
            adicionar(m, new Achado("java", "Versão do Java incompatível",
                    "O jogo (ou algum mod) não funciona com o Java usado. No modo automático o launcher escolhe Java 8 para versões antigas e 17/21 para as novas.",
                    "USAR JAVA AUTOMÁTICO", () -> {
                in.java = "";
                cfg.put("java", "");
                salvarConfig();
                return "Caminho do Java limpo: o launcher escolhe o Java certo ao jogar.";
            }));
        if (causas.contains("ram-menos")) {
            int novo = Math.max(1024, Math.min(ramAtual - 512, 2048));
            adicionar(m, new Achado("ram-menos", "RAM alta demais para este computador ou Java",
                    "A JVM não conseguiu reservar " + ramAtual + " MB. Isso acontece com pouca memória livre ou com Java de 32 bits (use o de 64 bits).",
                    "REDUZIR PARA " + novo + " MB", () -> { in.ram = novo; salvarConfig(); return "RAM desta instância: " + novo + " MB."; }));
        }
        if (causas.contains("ram-mais")) {
            int teto = fis > 0 ? (int) (fis * 6 / 10) : 8192;
            int novo = Math.min(ramAtual + 1024, teto) / 256 * 256;
            if (novo > ramAtual)
                adicionar(m, new Achado("ram-mais", "Faltou memória (OutOfMemoryError)",
                        "O jogo usou todos os " + ramAtual + " MB. Mods e shaders gastam mais memória.",
                        "AUMENTAR PARA " + novo + " MB", () -> { in.ram = novo; salvarConfig(); return "RAM desta instância: " + novo + " MB."; }));
            else
                adicionar(m, new Achado("ram-limite", "Faltou memória e a RAM já está perto do limite do PC",
                        "Use mods de otimização (Configuração > Vídeo e renderizador > Otimizar) ou diminua a distância de renderização.", null, null));
        }
        if (causas.contains("natives"))
            adicionar(m, new Achado("natives", "Bibliotecas nativas (LWJGL) ausentes ou corrompidas",
                    "Os arquivos .dll/.so do jogo não carregaram. Apagar e extrair de novo costuma resolver.",
                    "REFAZER NATIVES E VERIFICAR", () -> verificarEReparar(in)));
        if (causas.contains("corrompido"))
            adicionar(m, new Achado("corrompido", "Arquivo do jogo corrompido",
                    "Algum .jar/.zip está incompleto ou inválido (download interrompido, antivírus, disco).",
                    "VERIFICAR E CORRIGIR", () -> verificarEReparar(in)));
        if (causas.contains("loader") && !"Vanilla".equals(in.loader))
            adicionar(m, new Achado("reinstalar-loader", "Instalação do " + in.loader + " incompleta",
                    "O jogo não achou a classe principal do loader. Reinstalar apaga a instalação atual e baixa de novo ao jogar.",
                    "REINSTALAR " + in.loader.toUpperCase(), () -> reinstalarLoader(in)));
        if (causas.contains("mods")) {
            List<Path> sus = suspeitos(txt, mods);
            if (!sus.isEmpty())
                adicionar(m, new Achado("mods-suspeitos", "Mods citados no erro",
                        sus.size() + " mod(s) aparecem no registro: " + nomes(sus) + ".", "DESATIVAR ESSES MODS",
                        () -> desativarMods(in, sus)));
            if (!mods.isEmpty())
                adicionar(m, new Achado("mods-todos", "O erro parece vir dos mods",
                        "Jogar sem mods mostra se o problema é o Minecraft puro ou um mod (conflito, versão errada, dependência faltando).",
                        "TESTAR SEM MODS", () -> desativarMods(in, mods)));
        }
        if (causas.contains("config"))
            adicionar(m, new Achado("config", "Arquivo de configuração inválido",
                    "Algum arquivo da pasta config (JSON/TOML) está quebrado. É feito um backup e o jogo recria os arquivos.",
                    "REDEFINIR PASTA CONFIG", () -> redefinirConfig(in)));
        if (causas.contains("options"))
            adicionar(m, new Achado("options", "Arquivo options.txt com problema",
                    "As opções do jogo não puderam ser lidas. É feito um backup e o jogo cria outro.",
                    "REDEFINIR OPTIONS.TXT", () -> redefinirOptions(in)));
        if (causas.contains("video")) {
            Acao gpu = OS.equals("windows") ? () -> { cfg.put("gpu", "2"); gravarCfg(); return aplicarGpu(2); } : null;
            adicionar(m, new Achado("video", "Problema com a placa de vídeo ou OpenGL",
                    "O driver falhou ou o jogo está usando a GPU errada. Atualize o driver e prefira a placa dedicada. "
                            + "Em versões novas, se a opção existir, troque a API gráfica (OpenGL/Vulkan) em Opções > Vídeo.",
                    gpu != null ? "USAR PLACA DEDICADA" : null, gpu));
        }
        if (causas.contains("rede"))
            adicionar(m, new Achado("rede", "Problema de internet ao baixar arquivos",
                    "Confira a conexão, VPN, antivírus e firewall. O que já foi baixado é reaproveitado.",
                    "VERIFICAR ARQUIVOS", () -> verificarEReparar(in)));
        if (causas.contains("http404"))
            adicionar(m, new Achado("http404", "Arquivo não encontrado no servidor",
                    "A versão ou o loader escolhido pode não ter esse arquivo (ou o endereço mudou). Tente outra versão do loader.", null, null));
        if (causas.contains("sessao"))
            adicionar(m, new Achado("sessao", "Problema com a conta",
                    "A sessão expirou ou o login foi recusado.", "SAIR DA CONTA",
                    () -> { sairConta(); return "Conta desconectada. Entre de novo pelo botão Conta."; }));

        // verificações preventivas (não dependem do registro)
        if (fis > 0 && ramAtual > fis * 0.75) {
            int novo = Math.max(1024, (int) (fis / 2) / 256 * 256);
            adicionar(m, new Achado("ram-alta", "RAM configurada alta para este PC",
                    ramAtual + " MB de " + fis + " MB do computador. O Windows e outros programas também precisam de memória.",
                    "AJUSTAR PARA " + novo + " MB", () -> { in.ram = novo; salvarConfig(); return "RAM desta instância: " + novo + " MB."; }));
        }
        if ("Vanilla".equals(in.loader) && !mods.isEmpty())
            adicionar(m, new Achado("vanilla-mods", "Há mods na pasta, mas a instância é Vanilla",
                    "Mods só funcionam com Forge ou Fabric. Clique no lápis e escolha o tipo.", null, null));
        if ("Fabric".equals(in.loader) && !mods.isEmpty()
                && mods.stream().noneMatch(p -> p.getFileName().toString().toLowerCase().contains("fabric-api")))
            adicionar(m, new Achado("fabric-api", "Falta o Fabric API",
                    "A maioria dos mods Fabric exige o Fabric API na pasta mods.", "BAIXAR FABRIC API",
                    () -> baixarMod(in, "fabric-api")));
        if (!off.isEmpty())
            adicionar(m, new Achado("mods-reativar", off.size() + " mod(s) desativado(s)",
                    "Estão na pasta mods_desativados.", "REATIVAR MODS", () -> reativarMods(in)));

        // ações gerais
        adicionar(m, new Achado("verificar", "Verificar e baixar de novo os arquivos do jogo",
                "Confere os arquivos e apaga os corrompidos. Ao jogar, só eles são baixados de novo.",
                "VERIFICAR AGORA", () -> verificarEReparar(in)));
        if (!mods.isEmpty())
            adicionar(m, new Achado("mods-todos", "Testar o jogo sem mods",
                    "Move todos os mods para mods_desativados (dá para reativar depois).", "TESTAR SEM MODS",
                    () -> desativarMods(in, mods)));
        if (Files.exists(g.resolve("options.txt")))
            adicionar(m, new Achado("options", "Redefinir as opções do jogo",
                    "Faz backup do options.txt; o jogo cria um novo.", "REDEFINIR OPTIONS.TXT", () -> redefinirOptions(in)));
        adicionar(m, new Achado("pasta", "Abrir a pasta do jogo", "Veja mods, config, saves e registros.", "ABRIR PASTA",
                () -> { abrir(g); return "Pasta aberta."; }));
        return new ArrayList<>(m.values());
    }

    private static String nomes(List<Path> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size() && i < 5; i++) b.append(i > 0 ? ", " : "").append(l.get(i).getFileName());
        if (l.size() > 5) b.append(" e mais ").append(l.size() - 5);
        return b.toString();
    }

    // ----- correções -----
    static void apagarArvore(Path alvo, Path raiz) throws IOException {
        Path a = alvo.toAbsolutePath().normalize(), r = raiz.toAbsolutePath().normalize();
        if (!a.startsWith(r) || a.equals(r)) throw new IOException("Pasta fora da área do launcher: " + a);
        if (!Files.exists(a)) return;
        try (var s = Files.walk(a)) {
            for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }

    private String acharFabric(String mc, String lver) {
        String id = "fabric-loader-" + lver + "-" + mc;
        return Files.exists(BASE.resolve("versions").resolve(id).resolve(id + ".json")) ? id : null;
    }

    private String verificarEReparar(Inst in) throws Exception {
        Path libs = BASE.resolve("libraries");
        String launchId = in.mc;
        if ("Forge".equals(in.loader) && !in.lver.isEmpty()) {
            String f = acharForge(forgeBuild(in.mc, in.lver));
            if (f != null) launchId = f;
        } else if ("Fabric".equals(in.loader) && !in.lver.isEmpty()) {
            String f = acharFabric(in.mc, in.lver);
            if (f != null) launchId = f;
        }
        Map<String, Object> j = carregarMesclado(launchId);
        List<Object[]> alvos = new ArrayList<>();
        for (Object lo : list(j.get("libraries"))) {
            Map<String, Object> lib = map(lo);
            if (!rulesAllow(lib.get("rules"))) continue;
            if (lib.get("downloads") != null) {
                Map<String, Object> dl = map(lib.get("downloads"));
                if (dl.get("artifact") != null) {
                    Map<String, Object> a = map(dl.get("artifact"));
                    alvos.add(new Object[]{libs.resolve(str(a.get("path"))), str(a.get("sha1"))});
                }
                if (lib.get("natives") != null && dl.get("classifiers") != null) {
                    Object cl = map(lib.get("natives")).get(OS);
                    if (cl != null) {
                        Object co = map(dl.get("classifiers")).get(str(cl).replace("${arch}", IS64 ? "64" : "32"));
                        if (co != null) {
                            Map<String, Object> a = map(co);
                            alvos.add(new Object[]{libs.resolve(str(a.get("path"))), str(a.get("sha1"))});
                        }
                    }
                }
            } else if (str(lib.get("name")) != null) {
                alvos.add(new Object[]{libs.resolve(mavenPath(str(lib.get("name")))), null});
            }
        }
        Map<String, Object> cliente = map(map(j.get("downloads")).get("client"));
        alvos.add(new Object[]{BASE.resolve("versions").resolve(in.mc).resolve(in.mc + ".jar"), str(cliente.get("sha1"))});
        int total = 0, ruins = 0;
        for (Object[] a : alvos) {
            Path p = (Path) a[0];
            if (!Files.exists(p)) continue;
            total++;
            boolean ruim = Files.size(p) == 0 || (a[1] != null && !sha1(p).equalsIgnoreCase((String) a[1]));
            if (ruim) { Files.delete(p); ruins++; }
        }
        apagarArvore(BASE.resolve("versions").resolve(in.mc).resolve("natives"), BASE);
        return total + " arquivos verificados, " + ruins + " corrompido(s) apagado(s). A pasta natives será refeita.\n"
                + "Clique em JOGAR para baixar o que faltar.";
    }

    private String reinstalarLoader(Inst in) throws Exception {
        int n = 0;
        Path vs = BASE.resolve("versions");
        if ("Forge".equals(in.loader) && !in.lver.isEmpty()) {
            String id = acharForge(forgeBuild(in.mc, in.lver));
            if (id != null) { apagarArvore(vs.resolve(id), BASE); n++; }
            Files.deleteIfExists(BASE.resolve("installers").resolve("forge-" + in.lver + "-installer.jar"));
        } else if ("Fabric".equals(in.loader) && !in.lver.isEmpty()) {
            String id = acharFabric(in.mc, in.lver);
            if (id != null) { apagarArvore(vs.resolve(id), BASE); n++; }
        }
        if (Files.deleteIfExists(vs.resolve(in.mc).resolve(in.mc + ".json"))) n++;
        return "Instalação antiga removida (" + n + " item(ns)). Clique em JOGAR para instalar de novo.";
    }

    private String desativarMods(Inst in, List<Path> alvos) throws IOException {
        Path off = pastaDoJogo(in).resolve("mods_desativados");
        Files.createDirectories(off);
        int n = 0;
        for (Path p : alvos) {
            if (!Files.exists(p)) continue;
            Files.move(p, off.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            n++;
        }
        return n + " mod(s) movido(s) para mods_desativados. Clique em JOGAR para testar.";
    }

    private String reativarMods(Inst in) throws IOException {
        Path mods = pastaDoJogo(in).resolve("mods");
        Files.createDirectories(mods);
        int n = 0;
        for (Path p : jarsEm(pastaDoJogo(in).resolve("mods_desativados"))) {
            Files.move(p, mods.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            n++;
        }
        return n + " mod(s) reativado(s).";
    }

    private String redefinirOptions(Inst in) throws IOException {
        Path f = pastaDoJogo(in).resolve("options.txt");
        if (!Files.exists(f)) return "Não há options.txt (nada a fazer).";
        Path bak = f.resolveSibling("options.txt.bak-" + System.currentTimeMillis());
        Files.move(f, bak);
        return "options.txt movido para " + bak.getFileName() + ". O jogo cria um novo ao abrir.";
    }

    private String redefinirConfig(Inst in) throws IOException {
        Path f = pastaDoJogo(in).resolve("config");
        if (!Files.isDirectory(f)) return "Não há pasta config (nada a fazer).";
        Path bak = f.resolveSibling("config_backup_" + System.currentTimeMillis());
        Files.move(f, bak);
        return "Pasta config movida para " + bak.getFileName() + ". Os mods recriam as configurações.";
    }

    // ----- GPU (Windows) -----
    static void regGpu(String exe, int pref) {
        try {
            String key = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
            List<String> cmd = pref == 0 ? List.of("reg", "delete", key, "/v", exe, "/f")
                    : List.of("reg", "add", key, "/v", exe, "/t", "REG_SZ", "/d", "GpuPreference=" + pref + ";", "/f");
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    /** pref: 0 = automático, 1 = economia (integrada), 2 = alto desempenho (dedicada). */
    private String aplicarGpu(int pref) throws Exception {
        if (!OS.equals("windows")) throw new IllegalStateException("A escolha da placa de vídeo pelo launcher só existe no Windows.");
        Set<Path> homes = new LinkedHashSet<>();
        homes.add(Path.of(System.getProperty("java.home")));
        homes.addAll(javasInstalados());
        List<String> customs = new ArrayList<>();
        customs.add(cfgStr("java", ""));
        for (Inst i : instancias) customs.add(i.java);
        for (String c : customs) {
            if (c == null || c.isBlank()) continue;
            Path p = Path.of(c);
            if (Files.isRegularFile(p) && p.getParent() != null && p.getParent().getParent() != null) homes.add(p.getParent().getParent());
            else if (Files.isDirectory(p)) homes.add(p);
        }
        int n = 0;
        for (Path h : homes) {
            for (String nome : new String[]{"java.exe", "javaw.exe"}) {
                Path exe = h.resolve("bin").resolve(nome);
                if (!Files.exists(exe)) continue;
                regGpu(exe.toString(), pref);
                n++;
            }
        }
        String alvo = pref == 2 ? "alto desempenho (placa dedicada)" : pref == 1 ? "economia de energia (placa integrada)" : "automático";
        return "Preferência de vídeo \"" + alvo + "\" aplicada a " + n + " executável(is) do Java.\nFeche e abra o jogo para valer.";
    }

    // ----- Modrinth (mods de desempenho) -----
    static Map<String, Object> escolherVersao(String json) {
        Map<String, Object> pick = null;
        for (Object o : list(Json.parse(json))) {
            Map<String, Object> v = map(o);
            if (pick == null) pick = v;
            if ("release".equals(str(v.get("version_type")))) return v;
        }
        return pick;
    }

    static Map<String, Object> escolherArquivo(Map<String, Object> v) {
        Map<String, Object> arq = null;
        for (Object o : list(v.get("files"))) {
            Map<String, Object> f = map(o);
            if (arq == null || Boolean.TRUE.equals(f.get("primary"))) arq = f;
        }
        return arq;
    }

    static boolean jaInstalado(Path mods, String slug) {
        String k = slug.replace("-", "");
        for (Path p : jarsEm(mods))
            if (p.getFileName().toString().toLowerCase().replace("-", "").replace("_", "").contains(k)) return true;
        return false;
    }

    private Map<String, Object> modrinthVersao(String projeto, String mc, String loader) throws Exception {
        String url = MODRINTH + "project/" + URLEncoder.encode(projeto, StandardCharsets.UTF_8) + "/version?game_versions="
                + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8) + "&loaders="
                + URLEncoder.encode("[\"" + loader + "\"]", StandardCharsets.UTF_8);
        Resp r = http("GET", url, null, null, null);
        if (r.status() == 404) return null;
        if (r.status() != 200) throw new IOException("A Modrinth respondeu HTTP " + r.status());
        return escolherVersao(r.body());
    }

    private void modrinthBaixar(Map<String, Object> v, Path modsDir, String mc, String loader,
                                Set<String> vistos, List<String> out, int prof) throws Exception {
        Map<String, Object> arq = escolherArquivo(v);
        if (arq == null) return;
        String nome = str(arq.get("filename"));
        String url = str(arq.get("url"));
        if (nome == null || !nome.matches("[\\w.+\\- ()\\[\\]]+\\.jar") || url == null || !url.startsWith("https://cdn.modrinth.com/")) {
            out.add("ignorado (arquivo ou endereço inesperado): " + nome);
            return;
        }
        Path dest = modsDir.resolve(nome);
        boolean existia = Files.exists(dest);
        baixar(url, dest, str(map(arq.get("hashes")).get("sha1")));
        out.add((existia ? "já estava: " : "baixado: ") + nome);
        if (prof >= 2 || v.get("dependencies") == null) return;
        for (Object o : list(v.get("dependencies"))) {
            Map<String, Object> dep = map(o);
            if (!"required".equals(str(dep.get("dependency_type")))) continue;
            String pid = str(dep.get("project_id"));
            if (pid == null || !vistos.add(pid)) continue;
            Map<String, Object> dv = modrinthVersao(pid, mc, loader);
            if (dv == null) { out.add("dependência obrigatória sem versão compatível: " + pid); continue; }
            modrinthBaixar(dv, modsDir, mc, loader, vistos, out, prof + 1);
        }
    }

    private String baixarMod(Inst in, String slug) throws Exception {
        String loader = in.loader.toLowerCase();
        Path mods = pastaDoJogo(in).resolve("mods");
        Files.createDirectories(mods);
        Map<String, Object> v = modrinthVersao(slug, in.mc, loader);
        if (v == null) return "Não há versão de " + slug + " para " + in.mc + " (" + in.loader + ") na Modrinth.";
        List<String> out = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        vistos.add(str(v.get("project_id")));
        modrinthBaixar(v, mods, in.mc, loader, vistos, out, 0);
        return String.join("\n", out);
    }

    private String otimizarInstancia(Inst in, boolean ajustarJvm) throws Exception {
        StringBuilder rel = new StringBuilder();
        String loader = in.loader.toLowerCase();
        if (!"vanilla".equals(loader)) {
            Path mods = pastaDoJogo(in).resolve("mods");
            Files.createDirectories(mods);
            Set<String> vistos = new HashSet<>();
            for (List<String> grupo : "fabric".equals(loader) ? FABRIC_OTIM : FORGE_OTIM) {
                if (grupo.stream().anyMatch(s -> jaInstalado(mods, s))) {
                    rel.append("\u2022 ").append(grupo.get(0)).append(": já instalado\n");
                    continue;
                }
                Map<String, Object> achada = null;
                for (String slug : grupo) {
                    achada = modrinthVersao(slug, in.mc, loader);
                    if (achada != null) break;
                }
                if (achada == null) {
                    rel.append("\u2022 ").append(grupo.get(0)).append(": sem versão compatível para ").append(in.mc).append('\n');
                    continue;
                }
                vistos.add(str(achada.get("project_id")));
                List<String> out = new ArrayList<>();
                modrinthBaixar(achada, mods, in.mc, loader, vistos, out, 0);
                for (String s : out) rel.append("\u2022 ").append(s).append('\n');
            }
        } else if (!ajustarJvm) {
            return "A instância é Vanilla: mods de desempenho precisam de Fabric ou Forge.";
        }
        if (ajustarJvm) {
            if (in.jvm.contains("UseG1GC")) rel.append("\u2022 JVM: já tinha os ajustes\n");
            else {
                in.jvm = (in.jvm + " " + JVM_OTIM).trim();
                salvarConfig();
                rel.append("\u2022 JVM: ajustes de memória/GC aplicados a esta instância\n");
            }
        }
        return rel.toString();
    }

    // ----- janela -----
    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private String relatorio(Inst in, List<Achado> achados, String txt) {
        StringBuilder b = new StringBuilder();
        b.append("DirectLauncher | ").append(OSNAME).append(' ').append(ARCH).append(" | Java do launcher ").append(Runtime.version()).append('\n');
        b.append("Instância: ").append(in.name).append(" | Minecraft ").append(in.mc).append(" | ").append(in.loader)
                .append(in.lver.isEmpty() ? "" : " " + in.lver).append('\n');
        b.append("RAM: ").append(ramEfetiva(in)).append(" MB | Java: ")
                .append(!in.java.isBlank() ? in.java : cfgStr("java", "").isBlank() ? "automático" : cfgStr("java", "")).append('\n');
        b.append("Conta: ").append(contaTipo()).append('\n');
        b.append("Achados: ");
        for (Achado a : achados) b.append("[").append(a.titulo()).append("] ");
        b.append("\nLinhas-chave:\n");
        for (String l : linhasChave(txt)) b.append("  ").append(l).append('\n');
        String[] linhas = txt.split("\\R");
        b.append("--- registro (últimas 150 linhas) ---\n");
        for (int i = Math.max(0, linhas.length - 150); i < linhas.length; i++) b.append(linhas[i]).append('\n');
        return b.toString();
    }

    private void abrirResolver(Inst in) {
        if (in == null) { status("selecione uma instância."); return; }
        List<Achado> achados = diagnosticar(in);
        String txt = coletarRegistros(in);
        JDialog d = new JDialog(this, "Resolver problemas", true);
        JPanel raiz = new JPanel(new BorderLayout(0, 10));
        raiz.setBackground(COR_BG);
        raiz.setBorder(new EmptyBorder(14, 14, 14, 14));
        d.setContentPane(raiz);

        JPanel cab = new JPanel();
        cab.setLayout(new BoxLayout(cab, BoxLayout.Y_AXIS));
        cab.setOpaque(false);
        JLabel topo = new JLabel("<html><b style='font-size:14px'>" + esc(in.name) + "</b><br><span style='color:#95a5a6'>Minecraft "
                + esc(in.resumo()) + "</span></html>");
        topo.setForeground(Color.WHITE);
        cab.add(topo);
        List<String> chave = linhasChave(txt);
        cab.add(Box.createRigidArea(new Dimension(0, 8)));
        if (!chave.isEmpty()) {
            StringBuilder sb = new StringBuilder("<html><div style='width:690px'><b>Erro principal encontrado no registro:</b><br>");
            for (String l : chave) sb.append("&bull; ").append(esc(l)).append("<br>");
            sb.append("</div></html>");
            JLabel le = new JLabel(sb.toString());
            le.setForeground(COR_AVISO);
            cab.add(le);
        } else {
            JLabel ln = new JLabel("<html><div style='width:690px'>Nenhum erro registrado ainda. Jogue uma vez ou use as verificações abaixo.</div></html>");
            ln.setForeground(COR_CINZA);
            cab.add(ln);
        }
        raiz.add(cab, BorderLayout.NORTH);

        JPanel lista = new JPanel();
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        lista.setOpaque(false);
        for (Achado a : achados) {
            lista.add(cartao(d, a));
            lista.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        JScrollPane sp = new JScrollPane(lista);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        raiz.add(sp, BorderLayout.CENTER);

        JButton bCopiar = new JButton("COPIAR RELATÓRIO");
        estilizarBotao(bCopiar, COR_SEC);
        JButton bJogar = new JButton("JOGAR DE NOVO");
        estilizarBotao(bJogar, COR_JOGAR);
        JButton bFechar = new JButton("FECHAR");
        estilizarBotao(bFechar, COR_SEC);
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rodape.setOpaque(false);
        rodape.add(bCopiar);
        rodape.add(bFechar);
        rodape.add(bJogar);
        raiz.add(rodape, BorderLayout.SOUTH);
        bCopiar.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(relatorio(in, achados, txt)), null);
            status("relatório copiado. Cole no chat para pedir ajuda.");
        });
        bFechar.addActionListener(e -> d.dispose());
        bJogar.addActionListener(e -> { d.dispose(); atual = in; aoJogar(); });

        d.setSize(800, 640);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private JPanel cartao(JDialog d, Achado a) {
        JPanel c = new JPanel(new BorderLayout(12, 0));
        c.setBackground(COR_PAINEL);
        c.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel t = new JLabel("<html><div style='width:480px'><b>" + esc(a.titulo()) + "</b><br><span style='color:#95a5a6'>"
                + esc(a.detalhe()) + "</span></div></html>");
        t.setForeground(Color.WHITE);
        c.add(t, BorderLayout.CENTER);
        if (a.acao() != null) {
            JButton b = new JButton(a.botao());
            estilizarBotao(b, COR_DESTAQUE);
            b.setPreferredSize(new Dimension(200, 40));
            b.addActionListener(e -> {
                b.setEnabled(false);
                status("executando: " + a.titulo() + "...");
                new Thread(() -> {
                    try {
                        String r = a.acao().run();
                        SwingUtilities.invokeLater(() -> {
                            b.setText("FEITO");
                            JOptionPane.showMessageDialog(d, r, a.titulo(), JOptionPane.INFORMATION_MESSAGE);
                        });
                        status("pronto: " + a.titulo());
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            b.setEnabled(true);
                            JOptionPane.showMessageDialog(d, "Não deu certo: " + ex.getMessage(), a.titulo(), JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            });
            c.add(b, BorderLayout.EAST);
        }
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private void mostrarTexto(Component pai, String titulo, String texto) {
        JTextArea ta = new JTextArea(texto, 12, 58);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(pai, new JScrollPane(ta), titulo, JOptionPane.INFORMATION_MESSAGE);
    }

    private JLabel subtitulo(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        l.setBorder(new EmptyBorder(14, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JScrollPane rolagem(JPanel p) {
        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(COR_PAINEL);
        return sp;
    }

    // ===================== BACKUP =====================
    record ZipRes(long bytes, int arquivos, int pulados) {}

    record CacheRes(int arquivos, long bytes, List<Path> alvos) {}

    private ScheduledExecutorService agendador;

    static String carimbo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    }

    static String fmtData(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    static String fmtBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format("%.0f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }

    static long tamanho(Path p) {
        if (!Files.exists(p)) return 0;
        try (var s = Files.walk(p)) {
            return s.filter(Files::isRegularFile).mapToLong(f -> {
                try { return Files.size(f); } catch (IOException e) { return 0; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    static long ultimaModificacao(Path p) {
        if (!Files.exists(p)) return 0;
        try (var s = Files.walk(p)) {
            return s.filter(Files::isRegularFile).mapToLong(f -> f.toFile().lastModified()).max().orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    /** Compacta as pastas em um .zip (nomes relativos a baseDir). Grava em .part e só então renomeia. */
    static ZipRes zipar(Path baseDir, List<Path> origens, Path destZip) throws IOException {
        Files.createDirectories(destZip.getParent());
        Path tmp = destZip.resolveSibling(destZip.getFileName() + ".part");
        int n = 0, pulados = 0;
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (Path o : origens) {
                if (!Files.exists(o)) continue;
                List<Path> arquivos;
                try (var s = Files.walk(o)) { arquivos = s.filter(Files::isRegularFile).toList(); }
                for (Path f : arquivos) {
                    String nome = baseDir.relativize(f).toString().replace('\\', '/');
                    try {
                        z.putNextEntry(new ZipEntry(nome));
                        Files.copy(f, z);
                        z.closeEntry();
                        n++;
                    } catch (IOException e) {
                        pulados++;
                        try { z.closeEntry(); } catch (IOException ignored) {}
                    }
                }
            }
        }
        if (n == 0) { Files.deleteIfExists(tmp); return new ZipRes(0, 0, pulados); }
        Files.move(tmp, destZip, StandardCopyOption.REPLACE_EXISTING);
        return new ZipRes(Files.size(destZip), n, pulados);
    }

    private Path pastaBackups(Inst in) { return BASE.resolve("backups").resolve(in.dir); }

    private String fazerBackup(Inst in, String prefixo, List<String> subpastas, Path destDir) throws IOException {
        Path g = pastaDoJogo(in);
        List<Path> origens = new ArrayList<>();
        long est = 0;
        for (String sp : subpastas) {
            Path o = g.resolve(sp);
            if (Files.exists(o)) { origens.add(o); est += tamanho(o); }
        }
        if (origens.isEmpty() || est == 0) return null;
        Files.createDirectories(destDir);
        long livre = Files.getFileStore(destDir).getUsableSpace();
        if (livre < est + 100L * 1024 * 1024)
            throw new IOException("Espaço em disco insuficiente: o backup precisa de cerca de " + fmtBytes(est)
                    + " e há " + fmtBytes(livre) + " livres.");
        Path zip = destDir.resolve(prefixo + "_" + carimbo() + ".zip");
        ZipRes r = zipar(g, origens, zip);
        if (r.arquivos() == 0) return null;
        return "Backup criado:\n" + zip + "\n" + r.arquivos() + " arquivo(s), " + fmtBytes(r.bytes())
                + (r.pulados() > 0 ? "\n" + r.pulados() + " arquivo(s) estavam em uso e podem estar incompletos." : "");
    }

    private String backupMundos(Inst in) throws IOException {
        String r = fazerBackup(in, "mundos", List.of("saves"), pastaBackups(in));
        return r != null ? r : "Esta instância ainda não tem mundos (a pasta saves está vazia ou não existe).";
    }

    private String backupMods(Inst in) throws IOException {
        String r = fazerBackup(in, "mods-config", List.of("mods", "mods_desativados", "config"), pastaBackups(in));
        return r != null ? r : "Esta instância não tem mods nem configurações para guardar.";
    }

    private String backupCompleto(Inst in) throws IOException {
        String r = fazerBackup(in, "antes-de-atualizar", List.of("saves", "mods", "mods_desativados", "config"), pastaBackups(in));
        return r != null ? r : "Nada para guardar (instância vazia).";
    }

    /** Backup automático dos mundos: só se algo mudou desde o último, e guarda apenas os N mais recentes. */
    private String backupAuto(Inst in, int manter) throws IOException {
        Path destDir = BASE.resolve("backups").resolve("auto").resolve(in.dir);
        long mod = ultimaModificacao(pastaDoJogo(in).resolve("saves"));
        if (mod == 0 || mod <= ultimaModificacao(destDir)) return null;
        String r = fazerBackup(in, "auto", List.of("saves"), destDir);
        try (var s = Files.list(destDir)) {
            List<Path> zips = s.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed()).toList();
            for (int i = manter; i < zips.size(); i++) Files.deleteIfExists(zips.get(i));
        } catch (IOException ignored) {}
        return r;
    }

    private void iniciarBackupAuto(Inst in) {
        pararBackupAuto();
        if (!cfgBool("autoBackup", true)) return;
        int min = Math.max(1, cfgInt("autoMin", 15));
        int manter = Math.max(1, cfgInt("autoManter", 5));
        agendador = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-auto");
            t.setDaemon(true);
            return t;
        });
        agendador.scheduleWithFixedDelay(() -> {
            try {
                if (backupAuto(in, manter) != null)
                    status("backup automático feito às " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
            } catch (Exception e) {
                status("backup automático falhou: " + e.getMessage());
            }
        }, min, min, TimeUnit.MINUTES);
    }

    private void pararBackupAuto() {
        ScheduledExecutorService a = agendador;
        agendador = null;
        if (a != null) a.shutdownNow();
    }

    private void executarBackup(Component pai, boolean mundos) {
        Inst in = atual;
        if (in == null) { aviso(pai, "Crie ou selecione uma instância primeiro."); return; }
        if (jogoRodando()) { aviso(pai, "Pare o jogo antes: um backup com o jogo aberto pode sair incompleto."); return; }
        status("fazendo backup de " + in.name + "...");
        new Thread(() -> {
            try {
                String r = mundos ? backupMundos(in) : backupMods(in);
                status("backup concluído.");
                SwingUtilities.invokeLater(() -> mostrarTexto(pai, "Backup", r));
            } catch (Exception ex) {
                status("backup falhou: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> aviso(pai, "O backup falhou: " + ex.getMessage()));
            }
        }).start();
    }

    /** Fechar o launcher: se o jogo está aberto, oferece um backup antes. */
    private void aoFechar() {
        if (jogoRodando() && atual != null && !"direct".equals(modo)) {
            Object[] ops = {"Fazer backup e fechar", "Fechar sem backup", "Cancelar"};
            int r = JOptionPane.showOptionDialog(this, "O jogo ainda está aberto.\nFazer backup dos mundos antes de fechar o launcher?",
                    "Fechar o DirectLauncher", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, ops, ops[0]);
            if (r != 0 && r != 1) return;
            if (r == 0) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try { backupMundos(atual); } catch (Exception ex) {
                    int c = JOptionPane.showConfirmDialog(this, "O backup falhou: " + ex.getMessage() + "\n\nFechar mesmo assim?",
                            "Backup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (c != JOptionPane.YES_OPTION) { setCursor(Cursor.getDefaultCursor()); return; }
                }
            }
        }
        salvarConfig();
        System.exit(0);
    }

    // ===================== CACHE =====================
    /** Arquivos que podem ser apagados sem prejudicar nada (nunca saves, mods, config, assets ou libraries). */
    static CacheRes cacheLimpavel(Path base, long idadeMs) {
        long agora = System.currentTimeMillis();
        List<Path> alvos = new ArrayList<>();
        long total = 0;
        if (!Files.isDirectory(base)) return new CacheRes(0, 0, alvos);
        try (var s = Files.walk(base)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                Path rel = base.relativize(p);
                String nome = p.getFileName().toString().toLowerCase();
                long idade = agora - p.toFile().lastModified();
                String topo = rel.getName(0).toString();
                boolean limpar = false;
                if (nome.endsWith(".part") && idade > 3_600_000L) limpar = true;
                else if (topo.equals("runtime") && nome.matches("java\\d+\\.zip")) limpar = true;
                else if ((topo.equals("installers") || topo.equals("logs")) && rel.getNameCount() > 1 && idade > idadeMs) limpar = true;
                else if (rel.getNameCount() == 1 && nome.equals("forge-installer-log.txt") && idade > idadeMs) limpar = true;
                else if (topo.equals("instances") && rel.getNameCount() >= 4
                        && (rel.getName(2).toString().equals("crash-reports") || rel.getName(2).toString().equals("logs"))
                        && !nome.equals("latest.log") && idade > idadeMs) limpar = true;
                if (limpar) {
                    alvos.add(p);
                    try { total += Files.size(p); } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return new CacheRes(alvos.size(), total, alvos);
    }

    private CacheRes limparCache(boolean apagar, long idadeMs) {
        CacheRes r = cacheLimpavel(BASE, idadeMs);
        if (apagar) for (Path p : r.alvos()) { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }
        return r;
    }

    private void limparCacheAutomatico() {
        if (!cfgBool("autoCache", true)) return;
        new Thread(() -> {
            CacheRes r = limparCache(true, 7L * 24 * 3600 * 1000);
            if (r.arquivos() > 0) status("cache limpo: " + r.arquivos() + " arquivo(s), " + fmtBytes(r.bytes()) + " liberados.");
        }, "limpeza-cache").start();
    }

    private void limparCacheManual(Component pai) {
        long dia = 24L * 3600 * 1000;
        CacheRes prev = cacheLimpavel(BASE, dia);
        if (prev.arquivos() == 0) {
            JOptionPane.showMessageDialog(pai, "Não há cache para limpar.", "Limpar cache", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int r = JOptionPane.showConfirmDialog(pai, "Apagar " + prev.arquivos() + " arquivo(s) (" + fmtBytes(prev.bytes()) + ")?\n\n"
                        + "São downloads incompletos, instaladores já usados e logs/relatórios de crash com mais de 1 dia.\n"
                        + "Não mexe em saves, mods, configurações, assets nem libraries.", "Limpar cache",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        new Thread(() -> {
            CacheRes res = limparCache(true, dia);
            status("cache limpo: " + fmtBytes(res.bytes()) + " liberados.");
            SwingUtilities.invokeLater(() -> mostrarTexto(pai, "Limpar cache", res.arquivos() + " arquivo(s) apagado(s), " + fmtBytes(res.bytes()) + " liberados."));
        }).start();
    }

    // ===================== NOMES AUTOMÁTICOS =====================
    static String nomeAuto(String mc, String loader) {
        return mc + ("Vanilla".equals(loader) || loader == null ? "" : " " + loader);
    }

    private String nomeUnico(String base, Inst ignorar) {
        String cand = base;
        int n = 2;
        while (true) {
            final String c = cand;
            if (instancias.stream().noneMatch(i -> i != ignorar && i.name.equalsIgnoreCase(c))) return cand;
            cand = base + " (" + n++ + ")";
        }
    }

    // ===================== PAINEL DE INSTÂNCIAS =====================
    private void abrirPainel() {
        JDialog d = new JDialog(this, "Painel de instâncias", true);
        JPanel raiz = new JPanel(new BorderLayout(0, 10));
        raiz.setBackground(COR_BG);
        raiz.setBorder(new EmptyBorder(14, 14, 14, 14));
        d.setContentPane(raiz);

        String[] colunas = {"Instância", "Minecraft", "Tamanho", "Último jogo", "Mundos", "Mods", "Desativados", "Último backup"};
        DefaultTableModel mdl = new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) { case 2, 3, 7 -> Long.class; case 4, 5, 6 -> Integer.class; default -> String.class; };
            }
        };
        JTable tab = new JTable(mdl);
        tab.setAutoCreateRowSorter(true);
        tab.setRowHeight(28);
        tab.setBackground(COR_PAINEL);
        tab.setForeground(COR_TEXTO);
        tab.setGridColor(new Color(52, 60, 75));
        tab.setSelectionBackground(COR_DESTAQUE);
        tab.setSelectionForeground(Color.WHITE);
        tab.getTableHeader().setBackground(COR_SEC);
        tab.getTableHeader().setForeground(Color.WHITE);
        tab.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tab.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) { setText(v instanceof Long l ? fmtBytes(l) : ""); }
        });
        DefaultTableCellRenderer rData = new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) { setText(v instanceof Long l && l > 0 ? fmtData(l) : "nunca"); }

            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (!sel) comp.setForeground(v instanceof Long l && l == 0 ? COR_AVISO : COR_TEXTO);
                return comp;
            }
        };
        tab.getColumnModel().getColumn(3).setCellRenderer(rData);
        tab.getColumnModel().getColumn(7).setCellRenderer(rData);
        JScrollPane sp = new JScrollPane(tab);
        sp.getViewport().setBackground(COR_PAINEL);
        sp.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 90)));
        raiz.add(sp, BorderLayout.CENTER);

        JLabel info = new JLabel("Calculando tamanhos...");
        info.setForeground(COR_CINZA);
        JButton bSel = new JButton("SELECIONAR INSTÂNCIA");
        estilizarBotao(bSel, COR_DESTAQUE);
        JButton bPasta = new JButton("ABRIR PASTA");
        estilizarBotao(bPasta, COR_SEC);
        JButton bFechar = new JButton("FECHAR");
        estilizarBotao(bFechar, COR_SEC);
        JPanel sul = new JPanel(new BorderLayout());
        sul.setOpaque(false);
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botoes.setOpaque(false);
        botoes.add(bSel);
        botoes.add(bPasta);
        botoes.add(bFechar);
        sul.add(info, BorderLayout.WEST);
        sul.add(botoes, BorderLayout.EAST);
        raiz.add(sul, BorderLayout.SOUTH);

        Runnable irParaSelecionada = () -> { };
        bFechar.addActionListener(e -> d.dispose());
        bSel.addActionListener(e -> {
            int row = tab.getSelectedRow();
            if (row < 0) return;
            String nome = (String) mdl.getValueAt(tab.convertRowIndexToModel(row), 0);
            for (Inst i : instancias) if (i.name.equals(nome)) { atual = i; atualizarRodape(); salvarConfig(); }
            d.dispose();
        });
        bPasta.addActionListener(e -> {
            int row = tab.getSelectedRow();
            if (row < 0) return;
            String nome = (String) mdl.getValueAt(tab.convertRowIndexToModel(row), 0);
            for (Inst i : instancias) if (i.name.equals(nome)) abrir(pastaDoJogo(i));
        });

        new Thread(() -> {
            List<Object[]> linhas = new ArrayList<>();
            long totalInst = 0;
            for (Inst i : new ArrayList<>(instancias)) {
                Path g = pastaDoJogo(i);
                int mundos = 0;
                Path sv = g.resolve("saves");
                if (Files.isDirectory(sv)) {
                    try (var s = Files.list(sv)) { mundos = (int) s.filter(Files::isDirectory).count(); } catch (IOException ignored) {}
                }
                long tam = tamanho(g);
                totalInst += tam;
                Path log = g.resolve("launcher-log.txt");
                long jogou = Files.exists(log) ? log.toFile().lastModified() : 0L;
                long bk = Math.max(ultimaModificacao(pastaBackups(i)), ultimaModificacao(BASE.resolve("backups").resolve("auto").resolve(i.dir)));
                linhas.add(new Object[]{i.name, i.resumo().replaceAll("\\s+", " "), tam, jogou, mundos,
                        jarsEm(g.resolve("mods")).size(), jarsEm(g.resolve("mods_desativados")).size(), bk});
            }
            String resumo = "Instâncias: " + fmtBytes(totalInst) + "   |   assets: " + fmtBytes(tamanho(BASE.resolve("assets")))
                    + "   |   libraries: " + fmtBytes(tamanho(BASE.resolve("libraries")))
                    + "   |   backups: " + fmtBytes(tamanho(BASE.resolve("backups")));
            SwingUtilities.invokeLater(() -> {
                for (Object[] l : linhas) mdl.addRow(l);
                info.setText(resumo);
            });
        }).start();

        d.setSize(940, 520);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ===================== EDITOR DE SKIN =====================
    static final Set<String> SKINS_PADRAO = Set.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");

    static BufferedImage copia(BufferedImage o) {
        BufferedImage c = new BufferedImage(o.getWidth(), o.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(o, 0, 0, null);
        g.dispose();
        return c;
    }

    /** Skin inicial: cabeça, corpo, braços e pernas coloridos, com olhos e boca. */
    static BufferedImage novaBase() {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        Color pele = new Color(0xD9A47A), camisa = new Color(0x3AA6B9), calca = new Color(0x3B3F9B);
        g.setColor(pele);
        g.fillRect(0, 0, 32, 16);      // cabeça
        g.fillRect(40, 16, 16, 16);    // braço direito
        g.fillRect(32, 48, 16, 16);    // braço esquerdo
        g.setColor(camisa);
        g.fillRect(16, 16, 24, 16);    // corpo
        g.setColor(calca);
        g.fillRect(0, 16, 16, 16);     // perna direita
        g.fillRect(16, 48, 16, 16);    // perna esquerda
        g.setColor(Color.WHITE);
        im.setRGB(9, 12, Color.WHITE.getRGB());
        im.setRGB(10, 12, Color.WHITE.getRGB());
        im.setRGB(13, 12, Color.WHITE.getRGB());
        im.setRGB(14, 12, Color.WHITE.getRGB());
        im.setRGB(10, 12, new Color(0x2C3E50).getRGB());
        im.setRGB(13, 12, new Color(0x2C3E50).getRGB());
        im.setRGB(11, 14, new Color(0x7B3F00).getRGB());
        im.setRGB(12, 14, new Color(0x7B3F00).getRGB());
        g.dispose();
        return im;
    }

    /** Cópia espelhada em X de um retângulo, como o próprio jogo faz ao converter skins 64x32. */
    static void copyRect(BufferedImage im, int sx, int sy, int xo, int yo, int w, int h) {
        for (int i = 0; i < h; i++)
            for (int j = 0; j < w; j++)
                im.setRGB(sx + xo + j, sy + yo + i, im.getRGB(sx + (w - 1 - j), sy + i));
    }

    static BufferedImage converterLegacy(BufferedImage s) {
        BufferedImage o = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(s, 0, 0, null);
        g.dispose();
        copyRect(o, 4, 16, 16, 32, 4, 4);
        copyRect(o, 8, 16, 16, 32, 4, 4);
        copyRect(o, 0, 20, 24, 32, 4, 12);
        copyRect(o, 4, 20, 16, 32, 4, 12);
        copyRect(o, 8, 20, 8, 32, 4, 12);
        copyRect(o, 12, 20, 16, 32, 4, 12);
        copyRect(o, 44, 16, -8, 32, 4, 4);
        copyRect(o, 48, 16, -8, 32, 4, 4);
        copyRect(o, 40, 20, 0, 32, 4, 12);
        copyRect(o, 44, 20, -8, 32, 4, 12);
        copyRect(o, 48, 20, -16, 32, 4, 12);
        copyRect(o, 52, 20, -8, 32, 4, 12);
        return o;
    }

    /** Aceita 64x64 ou 64x32 (antiga, convertida); recusa qualquer outro tamanho. */
    static BufferedImage validarSkin(BufferedImage im) throws IOException {
        if (im == null) throw new IOException("Arquivo de imagem inválido.");
        if (im.getWidth() == 64 && im.getHeight() == 64) return copia(im);
        if (im.getWidth() == 64 && im.getHeight() == 32) return converterLegacy(im);
        throw new IOException("A skin precisa ter 64x64 (ou 64x32, a antiga). Esta tem " + im.getWidth() + "x" + im.getHeight() + ".");
    }

    /** Rosto 8x8 (com a camada do chapéu) para o avatar. */
    static BufferedImage rostoDe(BufferedImage skin) {
        BufferedImage r = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = r.createGraphics();
        g.drawImage(skin, 0, 0, 8, 8, 8, 8, 16, 16, null);
        g.drawImage(skin, 0, 0, 8, 8, 40, 8, 48, 16, null);
        g.dispose();
        return r;
    }

    private BufferedImage carregarRosto() {
        String n = cfgStr("skin", "");
        if (n.isEmpty()) return null;
        try {
            return rostoDe(validarSkin(ImageIO.read(BASE.resolve("skins").resolve(n + ".png").toFile())));
        } catch (Exception e) {
            return null;
        }
    }

    /** Cria o pacote de recursos que troca as skins padrão (jogadores sem skin) pela sua. */
    static String gerarPacoteSkin(Path jar, BufferedImage skin, Path destZip) throws IOException {
        List<String> alvos = new ArrayList<>();
        String packMcmeta = null;
        int altura = 64;
        try (ZipFile z = new ZipFile(jar.toFile())) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (n.equals("pack.mcmeta")) packMcmeta = new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
                if (n.startsWith("assets/minecraft/textures/entity/") && n.endsWith(".png")) {
                    String nome = n.substring(n.lastIndexOf('/') + 1, n.length() - 4);
                    if (SKINS_PADRAO.contains(nome)) alvos.add(n);
                }
            }
            if (!alvos.isEmpty()) {
                try (InputStream in = z.getInputStream(z.getEntry(alvos.get(0)))) {
                    BufferedImage ref = ImageIO.read(in);
                    if (ref != null) altura = ref.getHeight();
                }
            }
        }
        if (alvos.isEmpty())
            throw new IOException("Não achei as skins padrão dentro deste jogo (versão antiga demais ou com formato novo). "
                    + "Nessa versão a skin offline só aparece com um mod de skins.");
        BufferedImage usar = skin;
        if (altura == 32) { // jogos antigos só leem skin 64x32
            usar = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = usar.createGraphics();
            g.drawImage(skin, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(usar, "png", png);

        Map<String, Object> pack = new LinkedHashMap<>();
        if (packMcmeta != null) {
            try { // copia o formato de pacote do próprio jogo (pack_format ou min/max_format)
                for (Map.Entry<String, Object> e : map(map(Json.parse(packMcmeta)).get("pack")).entrySet())
                    if (!e.getKey().equals("description")) pack.put(e.getKey(), e.getValue());
            } catch (Exception ignored) {}
        }
        if (pack.isEmpty()) pack.put("pack_format", 1);
        pack.put("description", "Skin do DirectLauncher");

        Files.createDirectories(destZip.getParent());
        Path tmp = destZip.resolveSibling(destZip.getFileName() + ".part");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(Json.write(m("pack", pack)).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (String a : alvos) {
                out.putNextEntry(new ZipEntry(a));
                out.write(png.toByteArray());
                out.closeEntry();
            }
        }
        Files.move(tmp, destZip, StandardCopyOption.REPLACE_EXISTING);
        return "Pacote criado com " + alvos.size() + " textura(s) de skin padrão.";
    }

    /** Liga o pacote no options.txt conforme o formato da versão. Devolve null se não souber como. */
    static String ativarPacote(Path gameDir, String mc, String zipNome) {
        Matcher mt = Pattern.compile("^(\\d+)\\.(\\d+)").matcher(mc);
        if (!mt.find()) return null;
        int a = Integer.parseInt(mt.group(1)), b = Integer.parseInt(mt.group(2));
        int modo = a >= 2 || (a == 1 && b >= 13) ? 3 : (a == 1 && b >= 7) ? 2 : (a == 1 && b >= 6) ? 1 : 0;
        if (modo == 0) return null;
        if (modo == 1) { definirOpcao(gameDir, "skin", zipNome); return "ok"; }
        String entrada = modo == 3 ? "file/" + zipNome : zipNome;
        List<Object> lista = new ArrayList<>();
        try {
            Path f = gameDir.resolve("options.txt");
            if (Files.exists(f)) {
                for (String l : Files.readAllLines(f)) {
                    if (!l.startsWith("resourcePacks:")) continue;
                    Object o = Json.parse(l.substring("resourcePacks:".length()));
                    if (o instanceof List<?> ll) lista.addAll(ll);
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (modo == 3 && !lista.contains("vanilla")) lista.add(0, "vanilla");
        lista.remove(entrada);
        lista.add(entrada);
        definirOpcao(gameDir, "resourcePacks", Json.write(lista));
        return "ok";
    }

    private String aplicarSkinNoJogo(Inst in, BufferedImage skin) throws Exception {
        Path jar = BASE.resolve("versions").resolve(in.mc).resolve(in.mc + ".jar");
        if (!Files.exists(jar))
            throw new IOException("O jogo " + in.mc + " ainda não foi baixado. Clique em JOGAR uma vez e depois aplique a skin.");
        Path g = pastaDoJogo(in);
        String r = gerarPacoteSkin(jar, skin, g.resolve("resourcepacks").resolve("DirectLauncher-Skin.zip"));
        String ativ = ativarPacote(g, in.mc, "DirectLauncher-Skin.zip");
        return r + "\n" + (ativ != null ? "Pacote ativado nas opções da instância \"" + in.name + "\"."
                : "Ative em Opções > Pacotes de recursos: DirectLauncher-Skin.")
                + "\nA skin aparece só para você (jogadores sem skin usam a padrão)."
                + (jogoRodando() ? "\nFeche e abra o jogo para ver." : "");
    }

    /** Área de desenho 64x64 ampliada. */
    static final class SkinCanvas extends JComponent {
        static final int ESC = 8;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Color cor = new Color(0xC0392B);
        String ferramenta = "lapis";
        boolean guias = true;
        final Deque<BufferedImage> desfazer = new ArrayDeque<>();
        Runnable aoMudar = () -> { };
        Runnable aoPipeta = () -> { };

        SkinCanvas() {
            setPreferredSize(new Dimension(64 * ESC, 64 * ESC));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!"pipeta".equals(ferramenta)) guardar();
                    aplicar(e);
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if ("lapis".equals(ferramenta) || "borracha".equals(ferramenta)) aplicar(e);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void guardar() {
            desfazer.push(copia(img));
            while (desfazer.size() > 40) desfazer.removeLast();
        }

        void voltar() {
            if (desfazer.isEmpty()) return;
            img = desfazer.pop();
            repaint();
            aoMudar.run();
        }

        void aplicar(MouseEvent e) {
            int x = e.getX() / ESC, y = e.getY() / ESC;
            if (x < 0 || y < 0 || x >= 64 || y >= 64) return;
            switch (ferramenta) {
                case "lapis" -> img.setRGB(x, y, cor.getRGB() | 0xFF000000);
                case "borracha" -> img.setRGB(x, y, 0);
                case "balde" -> preencher(x, y, cor.getRGB() | 0xFF000000);
                case "pipeta" -> {
                    int p = img.getRGB(x, y);
                    if ((p >>> 24) != 0) { cor = new Color(p, false); aoPipeta.run(); }
                }
                default -> { }
            }
            repaint();
            aoMudar.run();
        }

        private int px(int x, int y) {
            int p = img.getRGB(x, y);
            return (p >>> 24) == 0 ? 0 : p;
        }

        void preencher(int x0, int y0, int novo) {
            int alvo = px(x0, y0);
            if (alvo == novo) return;
            Deque<int[]> fila = new ArrayDeque<>();
            fila.add(new int[]{x0, y0});
            while (!fila.isEmpty()) {
                int[] p = fila.poll();
                int x = p[0], y = p[1];
                if (x < 0 || y < 0 || x >= 64 || y >= 64 || px(x, y) != alvo) continue;
                img.setRGB(x, y, novo);
                fila.add(new int[]{x + 1, y});
                fila.add(new int[]{x - 1, y});
                fila.add(new int[]{x, y + 1});
                fila.add(new int[]{x, y - 1});
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) {
                    g.setColor(((x + y) & 1) == 0 ? new Color(70, 76, 90) : new Color(58, 63, 76));
                    g.fillRect(x * ESC, y * ESC, ESC, ESC);
                }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, 0, 0, 64 * ESC, 64 * ESC, null);
            if (guias) {
                g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                Object[][] regs = {
                        {0, 0, 32, 16, "Cabeça"}, {16, 16, 24, 16, "Corpo"}, {40, 16, 16, 16, "Braço D"},
                        {0, 16, 16, 16, "Perna D"}, {16, 48, 16, 16, "Perna E"}, {32, 48, 16, 16, "Braço E"},
                        {32, 0, 32, 16, "Chapéu (2ª camada)"}, {16, 32, 24, 16, "Jaqueta (2ª)"}, {40, 32, 16, 16, "Manga D (2ª)"},
                        {0, 32, 16, 16, "Calça D (2ª)"}, {0, 48, 16, 16, "Calça E (2ª)"}, {48, 48, 16, 16, "Manga E (2ª)"}};
                for (Object[] r : regs) {
                    int x = (int) r[0] * ESC, y = (int) r[1] * ESC, w = (int) r[2] * ESC, h = (int) r[3] * ESC;
                    g.setColor(new Color(255, 255, 255, 90));
                    g.drawRect(x, y, w - 1, h - 1);
                    g.setColor(new Color(255, 255, 255, 170));
                    g.drawString((String) r[4], x + 3, y + 12);
                }
            }
            g.dispose();
        }
    }

    /** Prévia de frente e de costas montada a partir da skin. */
    static final class SkinPreview extends JComponent {
        static final int E = 7;
        BufferedImage img;
        boolean fino;

        SkinPreview() { setPreferredSize(new Dimension(2 * 16 * E + 40, 32 * E + 30)); }

        private void parte(Graphics2D g, int ox, int oy, int sx, int sy, int sw, int sh, int dx, int dy) {
            int x = ox + dx * E, y = oy + dy * E;
            g.drawImage(img, x, y, x + sw * E, y + sh * E, sx, sy, sx + sw, sy + sh, null);
        }

        private void vista(Graphics2D g, int ox, int oy, boolean costas) {
            int aw = fino ? 3 : 4;
            int x0 = (16 - (8 + 2 * aw)) / 2;
            int corpoX = x0 + aw;
            if (!costas) {
                parte(g, ox, oy, 8, 8, 8, 8, corpoX, 0);
                parte(g, ox, oy, 4, 20, 4, 12, corpoX, 20);
                parte(g, ox, oy, 20, 52, 4, 12, corpoX + 4, 20);
                parte(g, ox, oy, 20, 20, 8, 12, corpoX, 8);
                parte(g, ox, oy, 44, 20, aw, 12, x0, 8);
                parte(g, ox, oy, 36, 52, aw, 12, corpoX + 8, 8);
                parte(g, ox, oy, 4, 36, 4, 12, corpoX, 20);
                parte(g, ox, oy, 4, 52, 4, 12, corpoX + 4, 20);
                parte(g, ox, oy, 20, 36, 8, 12, corpoX, 8);
                parte(g, ox, oy, 44, 36, aw, 12, x0, 8);
                parte(g, ox, oy, 52, 52, aw, 12, corpoX + 8, 8);
                parte(g, ox, oy, 40, 8, 8, 8, corpoX, 0);
            } else {
                parte(g, ox, oy, 24, 8, 8, 8, corpoX, 0);
                parte(g, ox, oy, 28, 52, 4, 12, corpoX, 20);
                parte(g, ox, oy, 12, 20, 4, 12, corpoX + 4, 20);
                parte(g, ox, oy, 32, 20, 8, 12, corpoX, 8);
                parte(g, ox, oy, fino ? 43 : 44, 52, aw, 12, x0, 8);
                parte(g, ox, oy, fino ? 51 : 52, 20, aw, 12, corpoX + 8, 8);
                parte(g, ox, oy, 12, 52, 4, 12, corpoX, 20);
                parte(g, ox, oy, 12, 36, 4, 12, corpoX + 4, 20);
                parte(g, ox, oy, 32, 36, 8, 12, corpoX, 8);
                parte(g, ox, oy, fino ? 59 : 60, 52, aw, 12, x0, 8);
                parte(g, ox, oy, fino ? 51 : 52, 36, aw, 12, corpoX + 8, 8);
                parte(g, ox, oy, 56, 8, 8, 8, corpoX, 0);
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            if (img == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            vista(g, 10, 22, false);
            vista(g, 16 * E + 30, 22, true);
            g.setColor(COR_CINZA);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.drawString("frente", 10 + 5 * E, 14);
            g.drawString("costas", 16 * E + 30 + 5 * E, 14);
            g.dispose();
        }
    }

    private void abrirEditorSkin() {
        JDialog d = new JDialog(this, "Editor de skin", true);
        SkinCanvas tela = new SkinCanvas();
        SkinPreview prev = new SkinPreview();
        Path pasta = BASE.resolve("skins");
        String[] nomeAtual = {cfgStr("skin", "")};
        BufferedImage ini = null;
        if (!nomeAtual[0].isEmpty()) {
            try { ini = validarSkin(ImageIO.read(pasta.resolve(nomeAtual[0] + ".png").toFile())); } catch (Exception ignored) {}
        }
        tela.img = ini != null ? ini : novaBase();
        prev.img = tela.img;
        tela.aoMudar = () -> { prev.img = tela.img; prev.repaint(); };

        JPanel raiz = new JPanel(new BorderLayout(14, 0));
        raiz.setBackground(COR_BG);
        raiz.setBorder(new EmptyBorder(14, 14, 14, 14));
        d.setContentPane(raiz);
        raiz.add(tela, BorderLayout.WEST);

        JPanel dir = new JPanel();
        dir.setLayout(new BoxLayout(dir, BoxLayout.Y_AXIS));
        dir.setOpaque(false);
        JLabel aviso = new JLabel("<html><div style='width:360px'>Sem conta (modo offline) a skin vale só para você. "
                + "Contas Microsoft e ely.by usam a skin do servidor da conta.</div></html>");
        aviso.setForeground(COR_CINZA);
        aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
        dir.add(aviso);
        dir.add(Box.createRigidArea(new Dimension(0, 8)));
        prev.setAlignmentX(Component.LEFT_ALIGNMENT);
        dir.add(prev);

        // ferramentas
        JPanel ferr = new JPanel(new GridLayout(1, 4, 6, 0));
        ferr.setOpaque(false);
        ButtonGroup grupo = new ButtonGroup();
        String[][] fs = {{"lapis", "Lápis"}, {"borracha", "Borracha"}, {"balde", "Balde"}, {"pipeta", "Conta-gotas"}};
        for (String[] f : fs) {
            JToggleButton b = new JToggleButton(f[1], f[0].equals("lapis"));
            b.setFocusPainted(false);
            b.addActionListener(e -> tela.ferramenta = f[0]);
            grupo.add(b);
            ferr.add(b);
        }
        ferr.setAlignmentX(Component.LEFT_ALIGNMENT);
        ferr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        dir.add(Box.createRigidArea(new Dimension(0, 8)));
        dir.add(ferr);

        // cores
        JButton bCor = new JButton("Cor atual (clique para escolher)");
        bCor.setBackground(tela.cor);
        bCor.setForeground(Color.WHITE);
        bCor.setFocusPainted(false);
        bCor.addActionListener(e -> {
            Color c = JColorChooser.showDialog(d, "Escolha a cor", tela.cor);
            if (c != null) { tela.cor = c; bCor.setBackground(c); }
        });
        tela.aoPipeta = () -> bCor.setBackground(tela.cor);
        JPanel pal = new JPanel(new GridLayout(2, 8, 3, 3));
        pal.setOpaque(false);
        int[] cores = {0x000000, 0xFFFFFF, 0x7F8C8D, 0x8B5A2B, 0xD9A47A, 0xC0392B, 0xE67E22, 0xF1C40F,
                0x27AE60, 0x1ABC9C, 0x3AA6B9, 0x2980B9, 0x3B3F9B, 0x8E44AD, 0xE91E63, 0x2C3E50};
        for (int c : cores) {
            JButton s = new JButton();
            s.setBackground(new Color(c));
            s.setPreferredSize(new Dimension(24, 22));
            s.setFocusPainted(false);
            s.addActionListener(e -> { tela.cor = new Color(c); bCor.setBackground(tela.cor); });
            pal.add(s);
        }
        bCor.setAlignmentX(Component.LEFT_ALIGNMENT);
        pal.setAlignmentX(Component.LEFT_ALIGNMENT);
        bCor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        dir.add(Box.createRigidArea(new Dimension(0, 8)));
        dir.add(bCor);
        dir.add(Box.createRigidArea(new Dimension(0, 6)));
        dir.add(pal);

        // opções
        JCheckBox cGuias = chk("Mostrar guias das partes do corpo", true);
        cGuias.addActionListener(e -> { tela.guias = cGuias.isSelected(); tela.repaint(); });
        JComboBox<String> cbModelo = new JComboBox<>(new String[]{"Modelo clássico (braço de 4 px)", "Modelo fino (braço de 3 px)"});
        cbModelo.addActionListener(e -> { prev.fino = cbModelo.getSelectedIndex() == 1; prev.repaint(); });
        JButton bDesfazer = new JButton("Desfazer");
        bDesfazer.addActionListener(e -> tela.voltar());
        JPanel linha1 = new JPanel(new BorderLayout(8, 0));
        linha1.setOpaque(false);
        linha1.add(cbModelo, BorderLayout.CENTER);
        linha1.add(bDesfazer, BorderLayout.EAST);
        linha1.setAlignmentX(Component.LEFT_ALIGNMENT);
        linha1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        cGuias.setAlignmentX(Component.LEFT_ALIGNMENT);
        dir.add(Box.createRigidArea(new Dimension(0, 8)));
        dir.add(cGuias);
        dir.add(Box.createRigidArea(new Dimension(0, 4)));
        dir.add(linha1);

        // arquivo
        JButton bCarregar = new JButton("CARREGAR SKIN.PNG");
        estilizarBotao(bCarregar, COR_SEC);
        JButton bNova = new JButton("CRIAR MINHA SKIN (BASE NOVA)");
        estilizarBotao(bNova, COR_SEC);
        JButton bSalvar = new JButton("SALVAR");
        estilizarBotao(bSalvar, COR_DESTAQUE);
        JButton bComo = new JButton("SALVAR COMO...");
        estilizarBotao(bComo, COR_SEC);
        JButton bUsar = new JButton("USAR NO LAUNCHER E NO JOGO");
        estilizarBotao(bUsar, COR_JOGAR);
        JPanel arq = new JPanel(new GridLayout(0, 2, 6, 6));
        arq.setOpaque(false);
        arq.add(bCarregar);
        arq.add(bNova);
        arq.add(bSalvar);
        arq.add(bComo);
        arq.setAlignmentX(Component.LEFT_ALIGNMENT);
        arq.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        bUsar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bUsar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        dir.add(Box.createVerticalGlue());
        dir.add(Box.createRigidArea(new Dimension(0, 10)));
        dir.add(arq);
        dir.add(Box.createRigidArea(new Dimension(0, 6)));
        dir.add(bUsar);
        raiz.add(dir, BorderLayout.CENTER);

        java.util.function.Supplier<Boolean> salvarSkin = () -> {
            try {
                String n = nomeAtual[0];
                if (n.isEmpty()) {
                    n = JOptionPane.showInputDialog(d, "Nome da skin (letras, números, - e _):", "minha-skin");
                    if (n == null) return false;
                    n = n.trim();
                    if (!n.matches("[\\w\\- ]{1,40}")) { aviso(d, "Nome inválido."); return false; }
                }
                Files.createDirectories(pasta);
                ImageIO.write(tela.img, "png", pasta.resolve(n + ".png").toFile());
                nomeAtual[0] = n;
                status("skin salva: " + n + ".png");
                return true;
            } catch (IOException ex) {
                aviso(d, "Não consegui salvar: " + ex.getMessage());
                return false;
            }
        };
        bSalvar.addActionListener(e -> { if (salvarSkin.get()) JOptionPane.showMessageDialog(d, "Skin salva em:\n" + pasta.resolve(nomeAtual[0] + ".png")); });
        bComo.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("skin.png"));
            fc.setFileFilter(new FileNameExtensionFilter("Imagem PNG", "png"));
            if (fc.showSaveDialog(d) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".png")) f = new File(f.getPath() + ".png");
            try { ImageIO.write(tela.img, "png", f); status("skin exportada: " + f.getName()); }
            catch (IOException ex) { aviso(d, "Não consegui salvar: " + ex.getMessage()); }
        });
        bCarregar.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Imagem PNG", "png"));
            if (fc.showOpenDialog(d) != JFileChooser.APPROVE_OPTION) return;
            try {
                BufferedImage lida = ImageIO.read(fc.getSelectedFile());
                boolean antiga = lida != null && lida.getHeight() == 32;
                BufferedImage v = validarSkin(lida);
                tela.guardar();
                tela.img = v;
                nomeAtual[0] = "";
                tela.aoMudar.run();
                tela.repaint();
                if (antiga) JOptionPane.showMessageDialog(d, "Skin antiga (64x32) convertida para 64x64.");
            } catch (IOException ex) {
                aviso(d, ex.getMessage());
            }
        });
        bNova.addActionListener(e -> {
            tela.guardar();
            tela.img = novaBase();
            nomeAtual[0] = "";
            tela.aoMudar.run();
            tela.repaint();
        });
        bUsar.addActionListener(e -> {
            if (!salvarSkin.get()) return;
            cfg.put("skin", nomeAtual[0]);
            gravarCfg();
            atualizarConta();
            Inst in = atual;
            if (in == null) { aviso(d, "Skin salva e usada no launcher. Crie uma instância para aplicar no jogo."); return; }
            BufferedImage c = copia(tela.img);
            bUsar.setEnabled(false);
            new Thread(() -> {
                try {
                    String r = aplicarSkinNoJogo(in, c);
                    SwingUtilities.invokeLater(() -> mostrarTexto(d, "Skin aplicada", r));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> aviso(d, "Skin salva e usada no launcher, mas no jogo não deu:\n" + ex.getMessage()));
                } finally {
                    SwingUtilities.invokeLater(() -> bUsar.setEnabled(true));
                }
            }).start();
        });

        d.setSize(64 * SkinCanvas.ESC + 470, 64 * SkinCanvas.ESC + 90);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ===================== BUSCAR MODS (Modrinth + link CurseForge) =====================
    record Hit(String slug, String titulo, String desc, long downloads) {
        @Override public String toString() { return titulo + "  \u2014  " + desc; }
    }

    static String loaderModrinth(String loader) {
        return switch (loader == null ? "" : loader) {
            case "Forge" -> "forge";
            case "Fabric" -> "fabric";
            case "NeoForge" -> "neoforge";
            case "Quilt" -> "quilt";
            default -> null;
        };
    }

    static List<Hit> parseHits(String json) {
        List<Hit> out = new ArrayList<>();
        for (Object o : list(map(Json.parse(json)).get("hits"))) {
            Map<String, Object> h = map(o);
            Object d = h.get("downloads");
            out.add(new Hit(str(h.get("slug")), nz(str(h.get("title"))), nz(str(h.get("description"))),
                    d instanceof Number n ? n.longValue() : 0L));
        }
        return out;
    }

    static String urlBusca(String termo, String mc, String loader) {
        String facets = "[[\"versions:" + mc + "\"],[\"categories:" + loader + "\"],[\"project_type:mod\"]]";
        return MODRINTH + "search?limit=25&query=" + URLEncoder.encode(termo, StandardCharsets.UTF_8)
                + "&facets=" + URLEncoder.encode(facets, StandardCharsets.UTF_8);
    }

    static String urlCurseForge(String termo) {
        return "https://www.curseforge.com/minecraft/search?search=" + URLEncoder.encode(termo, StandardCharsets.UTF_8);
    }

    private void abrirBuscaMods() {
        Inst in = atual;
        if (in == null) { aviso(this, "Crie ou selecione uma instância primeiro."); return; }
        String lo = loaderModrinth(in.loader);
        if (lo == null) {
            aviso(this, "Mods só funcionam em instâncias Forge, Fabric, NeoForge ou Quilt.\nClique no lápis e troque o tipo.");
            return;
        }
        JDialog d = new JDialog(this, "Buscar mods - " + in.name, true);
        JPanel raiz = new JPanel(new BorderLayout(0, 10));
        raiz.setBackground(COR_BG);
        raiz.setBorder(new EmptyBorder(14, 14, 14, 14));
        d.setContentPane(raiz);

        JTextField txt = campo("");
        JButton bBuscar = new JButton("BUSCAR");
        estilizarBotao(bBuscar, COR_DESTAQUE);
        JPanel topo = new JPanel(new BorderLayout(8, 0));
        topo.setOpaque(false);
        topo.add(txt, BorderLayout.CENTER);
        topo.add(bBuscar, BorderLayout.EAST);

        JPanel cab = new JPanel();
        cab.setLayout(new BoxLayout(cab, BoxLayout.Y_AXIS));
        cab.setOpaque(false);
        JLabel mostra = new JLabel("Mostrando mods para Minecraft " + in.mc + " (" + in.loader + ")");
        mostra.setForeground(COR_TEXTO);
        mostra.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel aviso = new JLabel("<html><div style='width:640px'>Aviso: a busca usa a Modrinth, que tem menos mods que o CurseForge, "
                + "principalmente para versões antigas. Se o mod que você procura não aparecer, ele pode estar só no CurseForge.</div></html>");
        aviso.setForeground(COR_AVISO);
        aviso.setAlignmentX(Component.LEFT_ALIGNMENT);
        cab.add(topo);
        cab.add(Box.createRigidArea(new Dimension(0, 8)));
        cab.add(mostra);
        cab.add(Box.createRigidArea(new Dimension(0, 4)));
        cab.add(aviso);
        raiz.add(cab, BorderLayout.NORTH);

        DefaultListModel<Hit> mdl = new DefaultListModel<>();
        JList<Hit> lista = new JList<>(mdl);
        lista.setBackground(COR_PAINEL);
        lista.setForeground(COR_TEXTO);
        lista.setSelectionBackground(COR_DESTAQUE);
        lista.setSelectionForeground(Color.WHITE);
        lista.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane sp = new JScrollPane(lista);
        sp.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 90)));
        raiz.add(sp, BorderLayout.CENTER);

        JButton bInstalar = new JButton("INSTALAR SELECIONADO");
        estilizarBotao(bInstalar, COR_JOGAR);
        JButton bCF = new JButton("PROCURAR NO CURSEFORGE");
        estilizarBotao(bCF, COR_SEC);
        JButton bFechar = new JButton("FECHAR");
        estilizarBotao(bFechar, COR_SEC);
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rodape.setOpaque(false);
        rodape.add(bCF);
        rodape.add(bInstalar);
        rodape.add(bFechar);
        raiz.add(rodape, BorderLayout.SOUTH);

        Runnable buscar = () -> {
            String termo = txt.getText().trim();
            if (termo.isEmpty()) { aviso(d, "Digite o nome do mod."); return; }
            bBuscar.setEnabled(false);
            status("buscando \"" + termo + "\" na Modrinth...");
            new Thread(() -> {
                try {
                    List<Hit> hits = parseHits(getText(urlBusca(termo, in.mc, lo)));
                    SwingUtilities.invokeLater(() -> {
                        mdl.clear();
                        for (Hit h : hits) mdl.addElement(h);
                        status(hits.size() + " mod(s) encontrado(s).");
                    });
                } catch (Exception ex) {
                    status("erro na busca: " + ex.getMessage());
                } finally {
                    SwingUtilities.invokeLater(() -> bBuscar.setEnabled(true));
                }
            }).start();
        };
        bBuscar.addActionListener(e -> buscar.run());
        txt.addActionListener(e -> buscar.run());

        bInstalar.addActionListener(e -> {
            Hit h = lista.getSelectedValue();
            if (h == null) { aviso(d, "Escolha um mod na lista."); return; }
            if (jogoRodando()) { aviso(d, "Feche o jogo antes de instalar mods."); return; }
            bInstalar.setEnabled(false);
            status("instalando " + h.titulo() + "...");
            new Thread(() -> {
                try {
                    String r = baixarMod(in, h.slug());
                    status(h.titulo() + " instalado.");
                    SwingUtilities.invokeLater(() -> mostrarTexto(d, "Instalação", r));
                } catch (Exception ex) {
                    status("erro ao instalar: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> aviso(d, "Não deu certo: " + ex.getMessage()));
                } finally {
                    SwingUtilities.invokeLater(() -> bInstalar.setEnabled(true));
                }
            }).start();
        });

        bCF.addActionListener(e -> {
            try {
                if (!Desktop.isDesktopSupported()) throw new IOException("navegador indisponível");
                Desktop.getDesktop().browse(URI.create(urlCurseForge(txt.getText().trim())));
            } catch (Exception ex) {
                aviso(d, "Não consegui abrir o navegador. Acesse curseforge.com e pesquise por lá.");
            }
        });
        bFechar.addActionListener(e -> d.dispose());

        d.setSize(760, 560);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ===================== ATALHOS / MODOS play E direct =====================
    /** Chamado quando a lista de versões termina de carregar: nos modos --play/--direct já inicia o jogo. */
    private void aoManifestoPronto() {
        if (modo == null) return;
        boolean direto = "direct".equals(modo);
        Inst alvo = null;
        for (Inst i : instancias) if (i.name.equalsIgnoreCase(alvoNome == null ? "" : alvoNome)) alvo = i;
        if (alvo == null) {
            aviso(this, "Instância não encontrada: " + alvoNome);
            if (direto) System.exit(1);
            return;
        }
        atual = alvo;
        atualizarRodape();
        if (direto) criarSplash(alvo);
        aoJogar();
    }

    private void criarSplash(Inst in) {
        splash = new JDialog((Frame) null, "DirectLauncher", false);
        JPanel p = new JPanel(new BorderLayout(14, 0));
        p.setBackground(COR_BG);
        p.setBorder(new EmptyBorder(16, 16, 16, 16));
        JLabel ic = new JLabel(new ImageIcon(logo.getScaledInstance(64, 64, Image.SCALE_SMOOTH)));
        JLabel t = new JLabel("Iniciando " + in.name);
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Segoe UI", Font.BOLD, 15));
        splashStatus = new JLabel("preparando...");
        splashStatus.setForeground(COR_CINZA);
        splashBarra = new JProgressBar(0, 100);
        splashBarra.setStringPainted(true);
        splashBarra.setForeground(COR_DESTAQUE);
        splashBarra.setBackground(COR_PAINEL);
        JPanel col = new JPanel(new GridLayout(0, 1, 0, 6));
        col.setOpaque(false);
        col.add(t);
        col.add(splashStatus);
        col.add(splashBarra);
        p.add(ic, BorderLayout.WEST);
        p.add(col, BorderLayout.CENTER);
        splash.setContentPane(p);
        splash.setIconImages(getIconImages());
        splash.setSize(470, 150);
        splash.setLocationRelativeTo(null);
        splash.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        splash.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });
        splash.setVisible(true);
    }

    private void dialogoSincrono(String msg) {
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, msg, "DirectLauncher", JOptionPane.ERROR_MESSAGE));
        } catch (Exception ignored) {}
    }

    /** Cria um atalho (.lnk) na Área de Trabalho: direto=false abre o launcher e inicia o jogo; direto=true abre só o jogo. */
    private void criarAtalho(boolean direto) {
        if (atual == null) { status("crie uma instância primeiro."); return; }
        if (!OS.equals("windows")) { aviso(this, "Atalhos automáticos só no Windows por enquanto."); return; }
        Inst in = atual;
        if (in.name.contains("\"")) { aviso(this, "Renomeie a instância sem aspas (\") para criar o atalho."); return; }
        new Thread(() -> {
            try {
                Path ico = garantirIco();
                String cp = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                String javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
                String args = "-cp \"" + cp + "\" Main " + (direto ? "--direct" : "--play") + " \"" + in.name + "\"";
                String arquivo = (direto ? "Minecraft - " : "DirectLauncher - ")
                        + in.name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".lnk";
                String ps = scriptAtalho(arquivo, javaw, args, BASE.toString(), ico.toString(),
                        direto ? "Abre só o Minecraft (" + in.name + ")" : "Abre o DirectLauncher e inicia " + in.name);
                Path f = Files.createTempFile("dl-atalho", ".ps1");
                Files.writeString(f, "\uFEFF" + ps, StandardCharsets.UTF_8);
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", f.toString())
                        .redirectErrorStream(true).start();
                String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int code = p.waitFor();
                Files.deleteIfExists(f);
                if (code != 0) throw new IOException(saida.isEmpty() ? "PowerShell falhou (código " + code + ")" : saida);
                status("atalho criado na Área de Trabalho: " + arquivo);
            } catch (Exception ex) {
                status("erro ao criar o atalho: " + ex.getMessage());
                erro("Não consegui criar o atalho:\n" + ex.getMessage());
            }
        }).start();
    }

    static String psq(String s) { return "'" + s.replace("'", "''") + "'"; }

    static String scriptAtalho(String arquivo, String alvo, String args, String pasta, String icone, String desc) {
        return "$d=[Environment]::GetFolderPath('Desktop');"
                + "$f=Join-Path $d " + psq(arquivo) + ";"
                + "$s=(New-Object -ComObject WScript.Shell).CreateShortcut($f);"
                + "$s.TargetPath=" + psq(alvo) + ";"
                + "$s.Arguments=" + psq(args) + ";"
                + "$s.WorkingDirectory=" + psq(pasta) + ";"
                + "$s.IconLocation=" + psq(icone) + ";"
                + "$s.Description=" + psq(desc) + ";"
                + "$s.Save();Write-Output $f";
    }

    /** Ícone do atalho = o logo do DirectLauncher (logo.ico), nunca o logo do Minecraft. */
    private Path garantirIco() throws IOException {
        Path dest = BASE.resolve("atalho.ico");
        for (Path dir : pastasDoLauncher()) {
            Path c = dir.resolve("logo.ico");
            if (Files.isRegularFile(c)) {
                if (!c.equals(dest)) Files.copy(c, dest, StandardCopyOption.REPLACE_EXISTING);
                return dest;
            }
        }
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(logo, 0, 0, 256, 256, null);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", png);
        Files.write(dest, icoDePng(png.toByteArray()));
        return dest;
    }

    /** .ico de uma imagem só (256x256) com PNG embutido. */
    static byte[] icoDePng(byte[] png) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(22 + png.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) 0).putShort((short) 1).putShort((short) 1);   // cabeçalho: tipo ICO, 1 imagem
        bb.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);       // largura/altura 0 = 256, sem paleta
        bb.putShort((short) 1).putShort((short) 32);                      // planos, bits por pixel
        bb.putInt(png.length).putInt(22);                                 // tamanho e posição dos dados
        bb.put(png);
        return bb.array();
    }

    // ===================== ÍCONE DA JANELA =====================
    /** Procura logo.ico / icon.png (pasta do launcher ou do projeto); sem arquivo, usa um ícone padrão. */
    private Image aplicarIcone() {
        List<Image> icones = new ArrayList<>();
        try {
            byte[] dados = null;
            boolean ico = false;
            for (String r : new String[]{"/logo.ico", "/logo.png", "/icon.png"}) { // logo embutido no .jar, se houver
                try (InputStream in = Main.class.getResourceAsStream(r)) {
                    if (in != null) { dados = in.readAllBytes(); ico = r.endsWith(".ico"); break; }
                }
            }
            if (dados == null) {
                busca:
                for (Path dir : pastasDoLauncher()) {
                    for (String n : new String[]{"logo.ico", "icon.png", "logo.png"}) {
                        Path f = dir.resolve(n);
                        if (Files.isRegularFile(f)) { dados = Files.readAllBytes(f); ico = n.endsWith(".ico"); break busca; }
                    }
                }
            }
            if (dados != null) {
                if (ico) {
                    icones.addAll(lerIco(dados));
                    Path guardado = BASE.resolve("logo.ico"); // guarda uma cópia: o launcher acha o logo mesmo sem o IntelliJ
                    if (!Files.exists(guardado)) Files.write(guardado, dados);
                } else {
                    Image im = ImageIO.read(new ByteArrayInputStream(dados));
                    if (im != null) icones.add(im);
                }
            }
        } catch (Exception ignored) {}
        if (icones.isEmpty()) icones.add(iconePadrao());
        setIconImages(icones);
        Image maior = icones.get(0);
        for (Image i : icones) if (i.getWidth(null) > maior.getWidth(null)) maior = i;
        return maior;
    }

    /** Pastas onde procurar o logo: dados do launcher, pasta do .jar/classes (e as de cima) e a pasta atual. */
    static List<Path> pastasDoLauncher() {
        List<Path> l = new ArrayList<>();
        l.add(BASE);
        try {
            Path cs = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path d = Files.isDirectory(cs) ? cs : cs.getParent();
            for (int i = 0; i < 5 && d != null; i++, d = d.getParent()) l.add(d);
        } catch (Exception ignored) {}
        l.add(Path.of(System.getProperty("user.dir")));
        return l;
    }

    /** Lê as imagens PNG embutidas num .ico (o formato usado pelos conversores atuais). */
    static List<Image> lerIco(byte[] b) {
        List<Image> out = new ArrayList<>();
        try {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int n = bb.getShort(4) & 0xFFFF;
            for (int k = 0; k < n; k++) {
                int e = 6 + k * 16;
                int size = bb.getInt(e + 8), off = bb.getInt(e + 12);
                if (off < 0 || size < 8 || off + size > b.length) continue;
                boolean png = (b[off] & 0xFF) == 0x89 && b[off + 1] == 'P' && b[off + 2] == 'N' && b[off + 3] == 'G';
                if (!png) continue; // entradas BMP antigas são ignoradas
                Image im = ImageIO.read(new ByteArrayInputStream(b, off, size));
                if (im != null) out.add(im);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private Image iconePadrao() {
        java.awt.image.BufferedImage i = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(COR_DESTAQUE);
        g.fillRoundRect(16, 16, 224, 224, 56, 56);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 120));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("DL", (256 - fm.stringWidth("DL")) / 2, (256 + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return i;
    }

    // ===================== UTILITÁRIOS DE UI =====================
    private void status(String t) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText("Status: " + t);
            if (splashStatus != null) splashStatus.setText(t);
        });
    }

    private void progresso(int n, int total) {
        SwingUtilities.invokeLater(() -> {
            barra.setMaximum(total);
            barra.setValue(n);
            if (splashBarra != null) { splashBarra.setMaximum(total); splashBarra.setValue(n); }
        });
    }

    private void erro(String msg) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE));
    }

    private void abrir(Path dir) {
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ex) {
            status("erro ao abrir pasta: " + ex.getMessage());
        }
    }

    private JLabel rotulo(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(COR_TEXTO);
        return l;
    }

    private void estilizarCampo(JTextField c) {
        c.setBackground(COR_BG);
        c.setForeground(Color.WHITE);
        c.setCaretColor(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 70, 90)), new EmptyBorder(3, 6, 3, 6)));
    }

    private void estilizarBotao(JButton b, Color cor) {
        b.setBackground(cor);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    // ===================== JSON MÍNIMO (sem bibliotecas) =====================
    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) { return (List<Object>) o; }

    static String str(Object o) { return o == null ? null : o.toString(); }

    static String nz(String s) { return s == null ? "" : s; }

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String s) { return new Json(s).val(); }

        static String write(Object o) {
            StringBuilder b = new StringBuilder();
            w(o, b);
            return b.toString();
        }

        private static void w(Object o, StringBuilder b) {
            if (o == null) {
                b.append("null");
            } else if (o instanceof String t) {
                b.append('"');
                for (char c : t.toCharArray()) {
                    switch (c) {
                        case '"' -> b.append("\\\"");
                        case '\\' -> b.append("\\\\");
                        case '\n' -> b.append("\\n");
                        case '\r' -> b.append("\\r");
                        case '\t' -> b.append("\\t");
                        default -> {
                            if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                            else b.append(c);
                        }
                    }
                }
                b.append('"');
            } else if (o instanceof Map<?, ?> mp) {
                b.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : mp.entrySet()) {
                    if (!first) b.append(',');
                    first = false;
                    w(String.valueOf(e.getKey()), b);
                    b.append(':');
                    w(e.getValue(), b);
                }
                b.append('}');
            } else if (o instanceof List<?> l) {
                b.append('[');
                for (int k = 0; k < l.size(); k++) {
                    if (k > 0) b.append(',');
                    w(l.get(k), b);
                }
                b.append(']');
            } else if (o instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
                b.append((long) (double) d);
            } else {
                b.append(o);
            }
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object val() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> mp = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return mp; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    i++; // ':'
                    mp.put(k, val());
                    ws();
                    if (s.charAt(i++) == '}') return mp;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(val());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(st, i));
        }

        private String string() {
            i++; // abre aspas
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> b.append(e);
                    }
                } else b.append(c);
            }
        }
    }

    public static void main(String[] args) {
        String modo = null, alvo = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--play") || args[i].equals("--direct")) {
                modo = args[i].substring(2);
                alvo = args[i + 1];
            }
        }
        final String fm = modo, fa = alvo;
        SwingUtilities.invokeLater(() -> {
            Main m = new Main(fm, fa);
            if (!"direct".equals(fm)) m.setVisible(true);
        });
    }
}