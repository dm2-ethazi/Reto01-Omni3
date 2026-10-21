namespace R01.Patronus.Api
{
    /// <summary>
    /// Configuración de acceso a la API de R01. Único punto donde se
    /// declara la base URL y los límites que impone el backend.
    /// </summary>
    public static class ApiConfig
    {
        /// <summary>Backend FastAPI por defecto (app/core/config.py: puerto 8000).</summary>
        public const string BaseUrlPorDefecto = "http://127.0.0.1:8000";

        /// <summary>El backend acepta limit entre 1 y 500 (por defecto 50).</summary>
        public const int LimitPorDefecto = 50;
        public const int LimitMaximo = 500;

        /// <summary>Longitud exacta del DNI en /central/estudiantes/{dni} (min 9, max 9).</summary>
        public const int LongitudDni = 9;

        public static string Normalizar(string baseUrl)
        {
            if (string.IsNullOrEmpty(baseUrl))
            {
                return BaseUrlPorDefecto;
            }
            return baseUrl.TrimEnd('/');
        }
    }
}
