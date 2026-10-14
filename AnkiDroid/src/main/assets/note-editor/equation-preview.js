// SPDX-License-Identifier: GPL-3.0-or-later

window.MathJax = {
    loader: { load: ["[tex]/mhchem", "[tex]/mathtools"] },
    tex: { packages: { "[+]": ["mhchem", "mathtools"] } },
    chtml: { adaptiveCSS: false },
    startup: { typeset: false },
    options: { enableMenu: false },
};

(() => {
    let revision = 0;
    let timer;
    let pending = Promise.resolve();

    window.setEquationPreview = input => {
        const current = ++revision;
        document.documentElement.style.setProperty("--foreground", input.foreground);
        document.documentElement.style.setProperty("--error", input.error);
        clearTimeout(timer);
        // Coalesce typing and serialize MathJax's asynchronous package loading.
        timer = setTimeout(() => {
            pending = pending.then(async () => {
                if (current !== revision) return;
                const preview = document.getElementById("preview");
                if (!input.text.trim()) {
                    preview.replaceChildren();
                    return;
                }
                try {
                    await MathJax.startup.promise;
                    if (current !== revision) return;
                    const text =
                        input.kind === "MATHJAX_CHEMISTRY" ? `\\ce{${input.text}}` : input.text;
                    const display = input.kind === "MATHJAX_BLOCK";
                    MathJax.texReset();
                    const rendered = await MathJax.tex2chtmlPromise(text, { display });
                    if (current === revision) {
                        MathJax.startup.document.updateDocument();
                        preview.replaceChildren(rendered);
                    }
                } catch (_) {
                    if (current !== revision) return;
                    const message = document.createElement("span");
                    message.className = "error";
                    message.textContent = input.unavailable;
                    preview.replaceChildren(message);
                }
            });
        }, 100);
    };
})();
