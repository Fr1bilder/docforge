package ru.fr1bilder.docforge;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

public final class App extends JFrame {
    private final Workspace workspace;
    private final DefaultListModel<Path> templates = new DefaultListModel<>();
    private final JList<Path> templateList = new JList<>(templates);
    private final Map<String,JTextField> inputs = new LinkedHashMap<>();
    private final JPanel form = new JPanel(new GridBagLayout());
    private final JTextArea preview = new JTextArea();
    private final JLabel status = new JLabel("Готово к работе");
    private final JLabel templateTitle = new JLabel("Выберите шаблон");
    private final JButton generate = new JButton("Создать документ");
    private final JButton batch = new JButton("Пакет из CSV");
    private final List<AbstractButton> actions = new ArrayList<>();
    private Path selected;

    private App(Workspace workspace) throws IOException {
        super("DocForge • Документы без рутины"); this.workspace=workspace;
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1180,790); setMinimumSize(new Dimension(960,650)); setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(new Color(245,247,249)); setContentPane(root);
        JPanel header = new JPanel(new BorderLayout(20,0)); header.setBorder(new EmptyBorder(22,26,20,26)); header.setBackground(Color.WHITE);
        JLabel brand = new JLabel("DocForge"); brand.setFont(brand.getFont().deriveFont(Font.BOLD,27)); brand.setForeground(new Color(23,92,82));
        header.add(brand,BorderLayout.WEST); header.add(new JLabel("ШАБЛОН → ДАННЫЕ → ГОТОВЫЙ DOCX"),BorderLayout.CENTER);
        JButton folder = button("Открыть документы", () -> open(workspace.output)); header.add(folder,BorderLayout.EAST); root.add(header,BorderLayout.NORTH);
        JPanel sidebar = new JPanel(new BorderLayout(0,14)); sidebar.setPreferredSize(new Dimension(280,0)); sidebar.setBackground(new Color(237,242,242)); sidebar.setBorder(new EmptyBorder(22,18,18,18));
        JLabel lib = new JLabel("БИБЛИОТЕКА ШАБЛОНОВ"); lib.setForeground(new Color(65,82,88)); sidebar.add(lib,BorderLayout.NORTH);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); templateList.setFixedCellHeight(60);
        templateList.setCellRenderer((list,value,index,isSelected,hasFocus)->{
            JLabel cell = new JLabel(value.getFileName().toString().replaceFirst("(?i)\\.docx$", ""));
            cell.setBorder(new EmptyBorder(10,12,10,8)); cell.setOpaque(true);
            cell.setBackground(isSelected?new Color(215,234,228):new Color(237,242,242));
            cell.setForeground(new Color(26,53,50)); return cell;
        });
        templateList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && templateList.isEnabled()) select(templateList.getSelectedValue()); });
        JScrollPane library = new JScrollPane(templateList); library.setBorder(BorderFactory.createEmptyBorder()); sidebar.add(library,BorderLayout.CENTER);
        JPanel sideActions = new JPanel(new GridLayout(0,1,0,9)); sideActions.setOpaque(false);
        sideActions.add(button("Добавить DOCX",this::importTemplate)); sideActions.add(button("История документов",this::history));
        sideActions.add(button("Папка рабочего пространства",()->open(workspace.root))); sideActions.add(button("Как подготовить шаблон",this::help));
        sidebar.add(sideActions,BorderLayout.SOUTH); root.add(sidebar,BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout(16,16)); content.setOpaque(false); content.setBorder(new EmptyBorder(22,24,14,24));
        JPanel heading = new JPanel(new BorderLayout(0,8)); heading.setOpaque(false);
        templateTitle.setFont(templateTitle.getFont().deriveFont(Font.BOLD,22)); heading.add(templateTitle,BorderLayout.NORTH);
        heading.add(new JLabel("Все поля обязательны. Исходный шаблон остаётся без изменений."),BorderLayout.SOUTH); content.add(heading,BorderLayout.NORTH);
        form.setBackground(Color.WHITE); form.setBorder(new EmptyBorder(18,18,18,18));
        preview.setEditable(false); preview.setLineWrap(true); preview.setWrapStyleWord(true); preview.setFont(new Font("Segoe UI",Font.PLAIN,14)); preview.setMargin(new Insets(18,18,18,18));
        JPanel previewPanel=new JPanel(new BorderLayout(0,8)); previewPanel.setOpaque(false); previewPanel.add(new JLabel("ПРОВЕРКА ДАННЫХ"),BorderLayout.NORTH); previewPanel.add(new JScrollPane(preview),BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(form),previewPanel); split.setResizeWeight(.58); split.setBorder(BorderFactory.createEmptyBorder()); content.add(split,BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0,12)); footer.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); buttons.setOpaque(false);
        generate.setBackground(new Color(23,92,82)); generate.setForeground(Color.WHITE); generate.setFont(generate.getFont().deriveFont(Font.BOLD)); generate.addActionListener(e->generate()); actions.add(generate);
        batch.addActionListener(e->batch()); actions.add(batch); buttons.add(generate); buttons.add(batch);
        buttons.add(button("Сохранить профиль",this::saveProfile)); buttons.add(button("Загрузить профиль",this::loadProfile)); footer.add(buttons,BorderLayout.NORTH);
        status.setBorder(new EmptyBorder(6,2,2,0)); status.setForeground(new Color(65,82,88)); footer.add(status,BorderLayout.SOUTH); content.add(footer,BorderLayout.SOUTH); root.add(content,BorderLayout.CENTER);
        reload();
        if (!templates.isEmpty()) { int index=0; for(int i=0;i<templates.size();i++)if(templates.get(i).getFileName().toString().startsWith("Коммерческое"))index=i; templateList.setSelectedIndex(index); }
    }

    private JButton button(String title, Runnable callback) { JButton b = new JButton(title); b.addActionListener(e -> callback.run()); actions.add(b); return b; }
    private void reload() throws IOException { templates.clear(); workspace.listTemplates().forEach(templates::addElement); }
    private void select(Path path) {
        selected=null;generate.setEnabled(false);batch.setEnabled(false);
        if(path==null)return;
        try {
            Set<String> fields=DocumentEngine.fields(path); selected=path; inputs.clear(); form.removeAll();
            templateTitle.setText(path.getFileName().toString().replaceFirst("(?i)\\.docx$", ""));
            Map<String,String> defaults=Map.of("Номер","КП-001","Дата",LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),"Компания","Мастерская решений","Клиент","Студия Север","Услуга","Разработка программы учёта заказов","Сумма","45000","Срок","10 рабочих дней","Контакт","demo@example.com","Примечание","Работы приняты без замечаний");
            int row=0;
            for(String key:fields){
                GridBagConstraints c=new GridBagConstraints(); c.gridx=0;c.gridy=row++;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTHWEST;c.insets=new Insets(0,0,5,0);
                form.add(new JLabel(key),c); JTextField field=new JTextField(defaults.getOrDefault(key,""),24); field.setMargin(new Insets(8,10,8,10)); inputs.put(key,field);
                c.gridy=row++;c.insets=new Insets(0,0,14,0);form.add(field,c);
                field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){updatePreview();}public void removeUpdate(javax.swing.event.DocumentEvent e){updatePreview();}public void changedUpdate(javax.swing.event.DocumentEvent e){updatePreview();}});
            }
            GridBagConstraints filler=new GridBagConstraints();filler.gridy=row;filler.weighty=1;form.add(Box.createVerticalGlue(),filler);
            updatePreview();form.revalidate();form.repaint();status.setText(fields.size()+" полей • "+path.getFileName());generate.setEnabled(true);batch.setEnabled(true);
        }catch(Exception ex){error(ex);}
    }
    private Map<String,String> values(){ Map<String,String> result=new LinkedHashMap<>();inputs.forEach((k,v)->result.put(k,v.getText().strip()));return result; }
    private void updatePreview(){ StringBuilder s=new StringBuilder("ДАННЫЕ ДОКУМЕНТА\n\n");values().forEach((k,v)->s.append(k).append("\n").append(v.isBlank()?"[не заполнено]":v).append("\n\n"));s.append("Сохранится оформление исходного DOCX.\nЭто список подстановок, а не постраничный предпросмотр.");preview.setText(s.toString());preview.setCaretPosition(0); }
    private void generate(){
        if(selected==null)return; Path template=selected;Map<String,String> data=values();
        Path destination=workspace.uniqueOutput(stem(template)+" — "+data.getOrDefault("Номер",LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
        work("Создание документа…",()->{DocumentEngine.render(template,destination,data);String warning="";try{workspace.record(destination,template.getFileName().toString(),"Один документ");}catch(IOException ex){warning=" • история не записана";}return "Создан: "+destination.getFileName()+warning;});
    }
    private void batch(){
        if(selected==null)return; JFileChooser chooser=new JFileChooser(workspace.root.toFile());chooser.setFileFilter(new FileNameExtensionFilter("CSV UTF-8 (*.csv)","csv"));
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path template=selected;Path source=chooser.getSelectedFile().toPath();
        work("Проверка и пакетная генерация…",()->{
            List<Map<String,String>> records=Csv.read(source);Set<String> required=DocumentEngine.fields(template);
            for(int i=0;i<records.size();i++){Map<String,String> record=records.get(i);List<String> missing=required.stream().filter(k->!record.containsKey(k)||record.get(k).isBlank()).toList();if(!missing.isEmpty())throw new IllegalArgumentException("Строка "+(i+2)+": не заполнены "+String.join(", ",missing));}
            int done=0;List<String> failures=new ArrayList<>();
            for(Map<String,String> record:records){try{Path dest=workspace.uniqueOutput(stem(template)+" — "+record.getOrDefault("Номер",Integer.toString(done+1)));DocumentEngine.render(template,dest,record);done++;try{workspace.record(dest,template.getFileName().toString(),"Пакет CSV");}catch(IOException log){failures.add("История: "+log.getMessage());}}catch(Exception ex){failures.add(ex.getMessage());}}
            return "Пакет: создано "+done+" из "+records.size()+(failures.isEmpty()?"":" • Ошибки: "+String.join("; ",failures));
        });
    }
    private void importTemplate(){
        JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("Шаблон DOCX","docx"));if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{Path path=chooser.getSelectedFile().toPath();Set<String> fields=DocumentEngine.fields(path);if(fields.isEmpty())throw new IllegalArgumentException("Поля не найдены. Добавьте в Word текст {{Клиент}} или другое имя поля.");Path dest=workspace.templates.resolve(path.getFileName());Files.copy(path,dest);reload();templateList.setSelectedValue(dest,true);}catch(Exception ex){error(ex);}
    }
    private void saveProfile(){String name=JOptionPane.showInputDialog(this,"Имя профиля:","Моя компания");if(name==null)return;try{Path target=workspace.profiles.resolve(name+".properties");if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"Заменить профиль?","Профиль",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;workspace.saveProfile(name,values());status.setText("Профиль сохранён: "+name);}catch(Exception ex){error(ex);}}
    private void loadProfile(){JFileChooser chooser=new JFileChooser(workspace.profiles.toFile());chooser.setFileFilter(new FileNameExtensionFilter("Профиль DocForge","properties"));if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{workspace.loadProfile(chooser.getSelectedFile().toPath()).forEach((k,v)->{if(inputs.containsKey(k))inputs.get(k).setText(v);});status.setText("Профиль загружен");}catch(Exception ex){error(ex);}}
    private void history(){
        try{DefaultTableModel model=new DefaultTableModel(new String[]{"Дата UTC","Режим","Шаблон","Файл"},0){public boolean isCellEditable(int r,int c){return false;}};workspace.history().forEach(model::addRow);JTable table=new JTable(model);table.setRowHeight(30);table.setAutoCreateRowSorter(true);JDialog dialog=new JDialog(this,"История документов",true);dialog.setSize(1000,420);dialog.setLocationRelativeTo(this);dialog.add(new JScrollPane(table));JButton open=new JButton("Открыть выбранный файл");open.addActionListener(e->{int row=table.getSelectedRow();if(row>=0)open(Path.of(model.getValueAt(table.convertRowIndexToModel(row),3).toString()));});dialog.add(open,BorderLayout.SOUTH);dialog.setVisible(true);}catch(Exception ex){error(ex);}
    }
    private void help(){JOptionPane.showMessageDialog(this,"1. Создайте DOCX в Word или LibreOffice.\n2. Вставьте поля: {{Клиент}}, {{Дата}}, {{Сумма}}.\n3. Добавьте файл в библиотеку и заполните форму.\n4. Для пакета: CSV в UTF-8, заголовки совпадают с именами полей.\n\nПоддерживаются текст, таблицы и колонтитулы.\nПоля внутри изображений, фигур и текстовых блоков не поддерживаются.\nPDF и автоматические расчёты в этой версии отсутствуют.\nПримеры используют вымышленные данные.","Работа с шаблонами",JOptionPane.INFORMATION_MESSAGE);}
    private void open(Path path){try{if(!Desktop.isDesktopSupported())throw new IOException("Открытие файлов недоступно: "+path);Desktop.getDesktop().open(path.toFile());}catch(Exception ex){error(ex);}}
    private void work(String message,Callable<String> action){
        actions.forEach(b->b.setEnabled(false));templateList.setEnabled(false);inputs.values().forEach(f->f.setEnabled(false));status.setText(message);
        new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return action.call();}protected void done(){actions.forEach(b->b.setEnabled(true));templateList.setEnabled(true);inputs.values().forEach(f->f.setEnabled(true));try{status.setText(get());}catch(Exception ex){error(ex.getCause()==null?ex:ex.getCause());}}}.execute();
    }
    private void error(Throwable ex){status.setText("Операция не завершена");JOptionPane.showMessageDialog(this,ex.getMessage()==null?ex.toString():ex.getMessage(),"Проверьте данные",JOptionPane.ERROR_MESSAGE);}
    private static String stem(Path p){return p.getFileName().toString().replaceFirst("(?i)\\.docx$","");}

    public static void main(String[] args)throws Exception{
        Path root=Workspace.defaultRoot();boolean demo=false;Path screenshot=null;
        for(int i=0;i<args.length;i++){switch(args[i]){case "--workspace"->root=Path.of(args[++i]);case "--demo"->demo=true;case "--screenshot"->screenshot=Path.of(args[++i]);default->throw new IllegalArgumentException("Аргументы: --workspace <папка> --demo --screenshot <png>");}}
        Workspace workspace=new Workspace(root);workspace.seed();
        if(demo){Path template=workspace.listTemplates().stream().filter(p->p.getFileName().toString().startsWith("Коммерческое")).findFirst().orElseThrow();int count=0;for(Map<String,String> row:Csv.read(workspace.root.resolve("Пример-пакет.csv"))){Path out=workspace.uniqueOutput("Демо-"+row.get("Номер"));DocumentEngine.render(template,out,row);workspace.record(out,template.getFileName().toString(),"CLI демо");System.out.println(out);count++;}System.out.println("Создано документов: "+count);return;}
        FlatLightLaf.setup();UIManager.put("defaultFont",new Font("Segoe UI",Font.PLAIN,13));UIManager.put("Button.arc",8);UIManager.put("Component.arc",8);
        Path shot=screenshot;
        SwingUtilities.invokeLater(()->{try{App app=new App(workspace);app.setVisible(true);if(shot!=null){javax.swing.Timer timer=new javax.swing.Timer(1300,e->{try{Path parent=shot.toAbsolutePath().getParent();Files.createDirectories(parent);java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(app.getWidth(),app.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();app.paint(g);g.dispose();javax.imageio.ImageIO.write(img,"png",shot.toFile());app.dispose();}catch(Exception ex){ex.printStackTrace();System.exit(1);}});timer.setRepeats(false);timer.start();}}catch(Exception ex){ex.printStackTrace();System.exit(1);}});
    }
}
