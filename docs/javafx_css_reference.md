# USMC TSO Jira Client: JavaFX CSS Style Reference Guide

This document is a comprehensive styling reference for editing or creating custom themes (e.g., `custom.css`, `dark.css`, `glass.css`). 

Because this application is built with **JavaFX** (and not standard HTML/web views), all custom styles override the default JavaFX theme, **Modena**. JavaFX CSS properties generally follow the standard W3C CSS specifications but are prefixed with `-fx-`.

---

## 1. Global Modena Theme Color Constants

JavaFX defines global color constants on the `.root` class. Changing these colors updates almost all default controls automatically.

| Selector / Variable | Description | Default / Example Value |
| :--- | :--- | :--- |
| `-fx-base` | The base color of controls (buttons, headers, scrollbar tracks). | `#1e293b` (Slate 800) |
| `-fx-background` | The main window background color. | `#0f172a` (Slate 900) |
| `-fx-control-inner-background` | The background color inside input textboxes, list views, and tables. | `#1e293b` |
| `-fx-control-inner-background-alt` | The alternate background color (e.g., for alternating rows in tables). | `#1e293b` |
| `-fx-accent` | The accent color for active items, selections, checkboxes, and focus highlights. | `#3b82f6` (Blue 500) |
| `-fx-focus-color` | The main glow color applied to a control when it gains keyboard focus. | `#3b82f6` |
| `-fx-faint-focus-color` | The outer, translucent glow ring surrounding a focused element. | `#3b82f633` (Blue with opacity) |
| `-fx-text-base-color` | The primary text color for text displayed on top of `-fx-base`. | `#f8fafc` |
| `-fx-text-background-color` | The primary text color for text displayed on top of `-fx-background`. | `#f8fafc` |

### Example `.root` Definition:
```css
.root {
    -fx-background: #0f172a;
    -fx-base: #1e293b;
    -fx-control-inner-background: #1e293b;
    -fx-accent: #3b82f6;
    -fx-text-base-color: #f8fafc;
    -fx-font-family: "Segoe UI", Arial, sans-serif;
    -fx-font-size: 13px;
}
```

---

## 2. Common CSS Properties (The `-fx-` Dialect)

JavaFX supports a subset of CSS properties. The most common properties used for styling containers, layout panes, buttons, and text fields are:

### Backgrounds
*   **`-fx-background-color`**: Background fill. Supports colors (hex, rgb, hsl, named), transparent, and gradients (linear/radial).
    *   *Example:* `-fx-background-color: linear-gradient(to bottom, #1e293b, #0f172a);`
*   **`-fx-background-radius`**: Radius of the background corners. Can specify 1 to 4 values (top-left, top-right, bottom-right, bottom-left).
    *   *Example:* `-fx-background-radius: 8px;`
*   **`-fx-background-insets`**: Offsets the background bounding box from the control's boundary.
    *   *Example:* `-fx-background-insets: 0 1 0 0;`

### Borders
*   **`-fx-border-color`**: Color of the border. Can specify individual values for each side.
    *   *Example:* `-fx-border-color: #334155;`
*   **`-fx-border-width`**: Thickness of the border.
    *   *Example:* `-fx-border-width: 1px;`
*   **`-fx-border-radius`**: Rounded corners of the border (should match `-fx-background-radius` for clean rendering).
    *   *Example:* `-fx-border-radius: 8px;`
*   **`-fx-border-style`**: Border stroke style (e.g., `solid`, `dashed`, `dotted`, or `none`).
    *   *Example:* `-fx-border-style: solid;`

### Fonts & Text
*   **`-fx-font-family`**: Font name (e.g., `"Segoe UI"`, `"Inter"`, `System`).
*   **`-fx-font-size`**: Size of the text (e.g., `13px`, `1.2em`).
*   **`-fx-font-weight`**: Thickness of text (`normal`, `bold`, `100`–`900`).
*   **`-fx-font-style`**: Font posture (`normal`, `italic`).
*   **`-fx-text-fill`**: Color of the text.
    *   *Example:* `-fx-text-fill: #ffffff;`

### Padding & Layout
*   **`-fx-padding`**: Padding inside a container or control.
    *   *Example:* `-fx-padding: 12px;`
*   **`-fx-spacing`**: Distance between children in a `VBox` or `HBox`.
*   **`-fx-hgap` / `-fx-vgap`**: Horizontal and vertical spacing inside a `GridPane` or `FlowPane`.
*   **`-fx-alignment`**: Alignment of children inside layout containers (e.g., `center`, `top-left`, `bottom-right`).

### Visual Effects
*   **`-fx-opacity`**: Transparency of the component (`0.0` to `1.0`).
*   **`-fx-cursor`**: Hover cursor style (`hand`, `default`, `text`, `wait`).
*   **`-fx-effect`**: Drop shadows, inner shadows, or blurs.
    *   *Example (Drop Shadow):* `-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 8, 0.0, 0, 4);`
    *   *Example (Inner Shadow):* `-fx-effect: innershadow(three-pass-box, black, 10, 0, 0, 0);`

---

## 3. UI Component Selectors & State Pseudo-Classes

Here is a map of JavaFX UI components used in the application and their corresponding selectors.

### Global State Pseudo-Classes
Any selector can be combined with a state pseudo-class:
*   `:hover` – When the mouse pointer is over the element.
*   `:focused` – When the element has keyboard focus.
*   `:pressed` – When the element is clicked/pressed.
*   `:selected` – When an item (tab, radio, checkbox) is selected.
*   `:disabled` – When a control is disabled.
*   `:showing` – When a dropdown or popover menu is active and open.

---

### Buttons
*   **`.button`** – Targets standard buttons.
    *   *Sub-States:* `.button:hover`, `.button:pressed`, `.button:disabled`
*   **`.list-action-btn`** – Custom class for Up/Down/Delete actions.
*   **`.action-btn-up` / `.action-btn-down` / `.action-btn-delete`** – Specialized action button colors.

### Text Inputs (TextFields & TextAreas)
*   **`.text-input`** – Base class styling both `TextField` and `TextArea`.
*   **`.text-field`** – Targets single-line text input fields.
*   **`.text-area`** – Targets multi-line text boxes.
    *   *Note:* `.text-area .content` can style the internal text scroll area.
*   **`.text-input:focused`** – Style applied during user input focus.

### Labels & Badges
*   **`.label`** – General text labels.
*   **`.badge`** – Base class for status tags (e.g., Create, Update, Link).
*   **`.badge-create` / `.badge-update` / `.badge-transition` / `.badge-link` / `.badge-asset` / `.badge-worklog`** – Subclasses defining background colors for different action types in the orchestrator.

### ComboBoxes (Dropdowns)
*   **`.combo-box`** – Outer dropdown container.
*   **`.combo-box > .list-cell`** – Selected item container showing in the box.
*   **`.combo-box > .text-field`** – Input area if the box is editable.
*   **`.combo-box > .arrow-button`** – The click area housing the arrow.
*   **`.combo-box > .arrow-button > .arrow`** – The SVG/glyph dropdown triangle indicator.
*   **`.combo-box-popup > .list-view`** – The scrolling dropdown menu overlay when clicked.
*   **`.combo-box-popup .list-cell`** – Individual list options.
    *   *State:* `.combo-box-popup .list-cell:hover`

### Tab Panes (Tabbed Interface)
*   **`.tab-pane`** – Outer container for the entire tabbed control.
*   **`.tab-header-area`** – The bar hosting all tab headers.
*   **`.tab`** – Individual tab header buttons.
    *   *States:* `.tab:hover`, `.tab:selected`
*   **`.tab-content-area`** – Main area underneath where panel content resides.

### Tables & List Views
*   **`.table-view` / `.list-view`** – Outer bounding container.
*   **`.table-view .column-header` / `.table-view .column-header-background`** – The header row containing headers.
*   **`.table-view .column-header .label`** – Text inside table header columns.
*   **`.table-row-cell`** – Individual rows.
    *   *States:* `.table-row-cell:filled:selected`, `.table-row-cell:odd`
*   **`.table-cell`** – Individual data cells.

### Scroll Bars
*   **`.scroll-bar:vertical` / `.scroll-bar:horizontal`** – The track container.
*   **`.scroll-bar .thumb`** – The draggable bar indicating page scroll.
    *   *State:* `.scroll-bar .thumb:hover`
*   **`.scroll-bar .increment-button` / `.scroll-bar .decrement-button`** – The arrow buttons on the ends.

### Panels & Cards (Custom Layout Styles)
These classes are added to custom panes in the Java source files:
*   **`.connection-panel`** – Styling for connection controls at the top.
*   **`.card`** – Standard panel wrapping sections (e.g., input card, status card).
*   **`.card-title`** – Sub-header label text at the top of a card.
*   **`.status-bar`** – Footer row containing status messages.
*   **`.status-text`** – Text inside the status bar.
*   **`.step-header`** – Section headers within the workflow steps.

---

## 4. Advanced: Gradients and Shadows

### Linear Gradients
Used to make headers or backgrounds look premium.
*   **Syntax**: `linear-gradient(to [direction], color1 percentage, color2 percentage, ...)`
*   *Example*: 
    ```css
    -fx-background-color: linear-gradient(to bottom right, #0f172a 0%, #1e293b 100%);
    ```

### Radial Gradients
Useful for spotlight backdrops or circular button glow.
*   **Syntax**: `radial-gradient(center x y, radius, color1, color2, ...)`
*   *Example*:
    ```css
    -fx-background-color: radial-gradient(center 50% 50%, radius 100%, #3b82f6, #1e293b);
    ```

### Glow Effects via drop shadow
Used for premium hover states on buttons.
*   **Syntax**: `dropshadow(type, color, radius, spread, offsetX, offsetY)`
*   *Example*:
    ```css
    -fx-effect: dropshadow(three-pass-box, rgba(59, 130, 246, 0.4), 8, 0, 0, 0);
    ```

---

## 5. Styling WebView HTML Content (Comment Summarizer & Workflow Report)

The **Comment Summarizer** output and the **Workflow Report** run inside an embedded web container (`WebView`). The active stylesheet is dynamically applied as the user stylesheet of the web engine, which allows you to style the HTML tags inside them directly.

### Selectors
*   `body` – The body background and text styling for both the AI summary and the workflow report.
*   `h2`, `h3` – Headers (e.g., "Workflow Report for...", "AI Summary").
*   `table` – The tabular log structure inside the workflow report.
*   `th`, `td` – Table columns, header cells, and data cells inside the workflow report.

### Example Styles:
```css
/* Style WebViews background and base text */
body {
    background-color: #0f172a;
    color: #f8fafc;
    font-family: "Segoe UI", Arial, sans-serif;
    margin: 10px;
}

/* Accent headers */
h2, h3 {
    color: #3b82f6; /* Or -custom-accent */
}

/* Style the report tables */
table {
    border-collapse: collapse;
    width: 100%;
    background-color: #1e293b;
}

th, td {
    border: 1px solid #334155;
    padding: 8px;
}

th {
    background-color: #334155;
}
```
