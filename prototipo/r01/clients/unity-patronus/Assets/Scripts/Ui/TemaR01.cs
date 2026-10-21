using UnityEngine;
using UnityEngine.UI;

namespace R01.Patronus.Ui
{
    /// <summary>
    /// Paleta y utilidades de estilo de R01 para Unity.
    /// Misma identidad visual que Android (res/values/colors.xml) y JavaFX
    /// (estilos.css): azul profundo de cabecera, acento cian y superficies
    /// claras. No depende de escenas ni de prefabs: sólo colores y ajustes
    /// básicos de componentes uGUI.
    /// </summary>
    public static class TemaR01
    {
        // ---------- Paleta (valores 0-1 para uGUI) ----------
        public static readonly Color Primario = Hex("#1E3A8A");
        public static readonly Color PrimarioOscuro = Hex("#172554");
        public static readonly Color PrimarioClaro = Hex("#E0E7FF");
        public static readonly Color Acento = Hex("#06B6D4");
        public static readonly Color Fondo = Hex("#F1F5F9");
        public static readonly Color Superficie = Hex("#FFFFFF");
        public static readonly Color Borde = Hex("#E2E8F0");
        public static readonly Color TextoPrincipal = Hex("#0F172A");
        public static readonly Color TextoSecundario = Hex("#64748B");
        public static readonly Color Exito = Hex("#10B981");
        public static readonly Color Error = Hex("#EF4444");

        /// <summary>Convierte un color hexadecimal #RRGGBB a Color de Unity.</summary>
        public static Color Hex(string rgb)
        {
            return ColorUtility.TryParseHtmlString(rgb, out Color color) ? color : Color.magenta;
        }

        /// <summary>Pinta una imagen con el color primario de cabecera.</summary>
        public static void PintarCabecera(Image imagen)
        {
            if (imagen != null) imagen.color = Primario;
        }

        /// <summary>Aplica el texto principal a un Text u objetivos similares.</summary>
        public static void PintarTexto(Text texto, Color color)
        {
            if (texto != null) texto.color = color;
        }

        /// <summary>
        /// Fondo de botón primario: relleno azul y color de texto blanco.
        /// Los estados pressed/hover se resuelven con un ColorBlock aparte.
        /// </summary>
        public static void AplicarBotonPrimario(BotonR01 boton)
        {
            if (boton == null) return;
            boton.Fondo.color = Primario;
            boton.Etiqueta.color = Superficie;
        }

        /// <summary>Fondo de botón secundario: contorno azul sobre blanco.</summary>
        public static void AplicarBotonSecundario(BotonR01 boton)
        {
            if (boton == null) return;
            boton.Fondo.color = Superficie;
            boton.Etiqueta.color = Primario;
        }

        /// <summary>Colores de interacción (normal / pressed / disabled) de un Button.</summary>
        public static ColorBlock BloqueInteraccion(Color normal)
        {
            var bloques = new ColorBlock
            {
                normalColor = normal,
                highlightedColor = normal * 1.12f,
                pressedColor = PrimarioOscuro,
                selectedColor = normal,
                disabledColor = new Color(0.58f, 0.64f, 0.72f, 0.6f),
                colorMultiplier = 1f,
                fadeDuration = 0.08f
            };
            return bloques;
        }
    }

    /// <summary>
    /// Componente mínimo que agrupa fondo y etiqueta de un botón R01 para
    /// poder estilizarlo desde el inspector o por código.
    /// Adjuntar al mismo GameObject que contiene el Image y asignar el Text.
    /// </summary>
    public class BotonR01 : MonoBehaviour
    {
        [SerializeField] private Image fondo;
        [SerializeField] private Text etiqueta;
        [SerializeField] private bool primario = true;

        public Image Fondo => fondo;
        public Text Etiqueta => etiqueta;

        private void Start()
        {
            if (primario)
            {
                TemaR01.AplicarBotonPrimario(this);
            }
            else
            {
                TemaR01.AplicarBotonSecundario(this);
            }
        }
    }
}
