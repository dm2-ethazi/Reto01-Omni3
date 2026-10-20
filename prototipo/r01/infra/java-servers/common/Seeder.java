import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * R01 - Sembrador JDBC generico.
 *
 * Ejecuta un fichero .sql dividiendo el contenido por ';' y descartando
 * los comentarios que empiezan por '--'. Se usa dentro de los contenedores
 * de Derby, HSQLDB y H2 para cargar la semilla en la primera ejecucion.
 *
 * Uso: Seeder &lt;jdbc-url&gt; &lt;usuario&gt; &lt;contrasena&gt; &lt;script.sql&gt;
 */
public final class Seeder {

    private Seeder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && "--ping".equals(args[0])) {
            // Modo salud: sólo abre y cierra una conexión.
            open(args[1], args[2], args[3]).close();
            System.out.println("PING_OK");
            return;
        }
        if (args.length != 4) {
            System.err.println("Uso: Seeder <jdbc-url> <usuario> <password> <script.sql>");
            System.err.println("     Seeder --ping <jdbc-url> <usuario> <password>");
            System.exit(2);
        }
        String url = args[0];
        String user = args[1];
        String password = args[2];
        List<String> statements = splitStatements(Files.readString(Path.of(args[3]), StandardCharsets.UTF_8));

        try (Connection connection = open(url, user, password)) {
            for (String sql : statements) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
        System.out.println("SEED_OK sentencias=" + statements.size());
    }

    private static Connection open(String url, String user, String password) throws Exception {
        boolean withCredentials = !user.isEmpty() || !password.isEmpty();
        return withCredentials
                ? DriverManager.getConnection(url, user, password)
                : DriverManager.getConnection(url);
    }

    private static List<String> splitStatements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1);
                }
                if (!sql.isBlank()) {
                    result.add(sql);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isBlank()) {
            result.add(tail);
        }
        return result;
    }
}
