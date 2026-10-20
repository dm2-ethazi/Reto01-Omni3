"""Cliente mock de escritorio (Tkinter) del prototipo R01.

Contra la API en curso: /health, /estado, /estudiantes y la cocina de sync.
Uso:  python clientes_mock/escritorio/r01_client.py
"""

import json
import threading
import tkinter as tk
import tkinter.messagebox as mb
import urllib.request
from tkinter import ttk

URL = "http://127.0.0.1:8010"


def get(path: str, timeout: float = 4.0):
    with urllib.request.urlopen(URL + path, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


class App(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("R01 · Prototipo Omni3 (clientes_mock)")
        self.geometry("760x480")
        self.configure(bg="#F1F5F9")

        top = tk.Frame(self, bg="#1E3A8A", pady=10, padx=12)
        top.pack(fill="x")
        tk.Label(top, text="PROTOTIPO R01 - Sincronizador", fg="white",
                 bg="#1E3A8A", font=("Segoe UI", 14, "bold")).pack(side="left")

        barra = tk.Frame(self, bg="#F1F5F9", pady=8, padx=12)
        barra.pack(fill="x")
        tk.Label(barra, text="URL:", bg="#F1F5F9").pack(side="left")
        self.campo_url = tk.Entry(barra, width=34)
        self.campo_url.insert(0, URL)
        self.campo_url.pack(side="left", padx=6)
        for texto, accion in (("Health", self.health), ("Estado", self.estado),
                              ("Sincronizar", self.sincronizar), ("Refrescar", self.refrescar)):
            tk.Button(barra, text=texto, command=accion, bg="#06B6D4", fg="white",
                      relief="flat", padx=10, pady=3).pack(side="left", padx=3)

        cols = ("id", "nombre", "casa", "origen")
        self.tabla = ttk.Treeview(self, columns=cols, show="headings", height=14)
        for c, w in zip(cols, (50, 220, 140, 120)):
            self.tabla.heading(c, text=c.upper())
            self.tabla.column(c, width=w, anchor="w")
        self.tabla.pack(fill="both", expand=True, padx=12, pady=6)

        self.estado_bar = tk.Label(self, text="listo", anchor="w", bg="#E2E8F0",
                                   padx=12, pady=4)
        self.estado_bar.pack(fill="x", side="bottom")

    def _set(self, txt: str) -> None:
        self.estado_bar.config(text=txt)

    def _async(self, fn) -> None:
        threading.Thread(target=lambda: self._run(fn), daemon=True).start()

    def _run(self, fn) -> None:
        global URL
        URL = self.campo_url.get().strip()
        try:
            fn()
        except Exception as exc:
            self.after(0, lambda: (self._set(f"ERROR: {exc}"),
                                   mb.showerror("R01", str(exc))))

    def health(self) -> None:
        def h():
            datos = get("/health")
            self.after(0, lambda: self._set(
                f"/health -> {datos}"))
        self._async(h)

    def estado(self) -> None:
        def e():
            datos = get("/estado")
            self.after(0, lambda: self._set(f"/estado -> {datos}"))
        self._async(e)

    def sincronizar(self) -> None:
        def s():
            import sys
            from pathlib import Path
            raiz = Path(__file__).resolve().parents[2]
            sys.path.insert(0, str(raiz))
            from servicios.cocina import sincronizar
            res = sincronizar()
            self.after(0, lambda: (
                self._set(f"sincronizado: {res['total']} filas -> {res['por_origen']}"),
                self._cargar()))
        self._async(s)

    def refrescar(self) -> None:
        self._cargar()

    def _cargar(self) -> None:
        def c():
            datos = get("/estudiantes")
            filas = datos.get("estudiantes", [])
            def pintar():
                self.tabla.delete(*self.tabla.get_children())
                for f in filas:
                    self.tabla.insert("", "end", values=(
                        f.get("id"), f.get("nombre"), f.get("casa"), f.get("origen")))
                self._set(f"{datos.get('total', len(filas))} estudiantes en el almacen")
            self.after(0, pintar)
        self._async(c)


if __name__ == "__main__":
    App().mainloop()
