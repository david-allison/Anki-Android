# Development Compose note editor

Enable **Settings → Developer options → Work in progress → Compose note editor**.
The existing editor remains the default. Image Occlusion and Aedict keep their
existing editor flows.

This editor requires the backend's version 1 `/editor-fields` page. Build the
backend with that component, then configure `local.properties`:

```properties
local_backend=true
# Optional: defaults to ../Anki-Android-Backend
local_backend_path=/absolute/path/to/Anki-Android-Backend
```

The backend renders only Anki's rich-text and CodeMirror HTML-source fields.
Compose owns the toolbar, deck/type/tags, media pickers, and explicit Save.
Editing closes after Save; ordinary Add starts another note with sticky fields
retained. Tablets show the unsaved card preview alongside the fields.

Field HTML remains in the WebView. Snapshots serve Save and the approximately
100 ms live preview. IndexedDB stores fields, their clean baseline, and opaque
native recovery metadata together; native preferences store only discovery IDs.
Delayed insertions retain the original field and selection, and fail visibly
if that field or note has changed.

The fields page uses a fixed virtual HTTPS origin in the app's private WebView
storage. Only bundled assets and collection media are served. The native web
bridge exposes the field protocol and required translation request, not generic
collection access.

Development limitations: the backend's uncommitted card renderer does not accept
a preview deck ID, so `{{Deck}}`/`{{Subdeck}}` show the saved deck for Edit and a
placeholder for Add. Draft recovery restores completed checkpoints; it is not a
transaction spanning WebView storage and a collection Save.
