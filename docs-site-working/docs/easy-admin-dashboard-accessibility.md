# Easy BPM Admin Dashboard: Accessibility Guide

## Introduction

The Easy BPM Admin Dashboard is designed to be fully accessible to users with disabilities, following **WCAG 2.1 Level AA** standards. This guide explains accessibility features and how to use them effectively.

---

## Accessibility Features

### 1. Keyboard Navigation

Navigate entire dashboard without using mouse:

#### Tab Navigation

**Press Tab** to move forward through interactive elements in order:

```
[Dashboard Header] → [Period Selector] → [Metric Cards] → 
[Refresh Button] → [Tab Navigation] → [Table/List] → 
[Pagination] → [Back to Header]
```

**Press Shift+Tab** to move backward through elements:

```
[Back one element] ← [Current Focus]
```

#### Using Tab Effectively

1. **Start**: Click in browser window, press Tab
2. **Move**: Press Tab repeatedly to navigate
3. **Focus Ring**: Blue ring appears around focused element
4. **Activate**: Press Enter to activate button/link
5. **Exit Field**: Press Tab again to move to next element

#### Example Navigation Flow

```
Start → Tab → [24h Period Button]
Focus Ring: Visible blue ring around "24h"
Action: Press Enter to select 24h period
Result: Dashboard filters to last 24 hours
```

---

### 2. Focus Indicators

All interactive elements show clear visual focus indicator:

#### What to Look For

- **Blue ring** around focused element (2px border)
- **Ring has offset** (visible even on colored backgrounds)
- **No element has invisible focus** (all visible when focused)

#### Focus Ring Customization

If focus ring barely visible:
1. Browser Settings → Colors/Accessibility
2. Increase focus ring size or contrast
3. Some browsers allow custom focus colors

#### Example Focus States

```
Button (unfocused):      [Click Me]
Button (focused):        [Click Me] ← Blue ring visible
Button (hovered):        [Click Me] ← Blue ring + hover color
Button (activated):      [Click Me] ← Ring shows press state
```

---

### 3. Keyboard Shortcuts

| Key | Action | Context |
|-----|--------|---------|
| **Tab** | Move to next element | Everywhere |
| **Shift+Tab** | Move to previous element | Everywhere |
| **Enter** | Activate button/link | When focused on button |
| **Space** | Toggle checkbox/select | When focused on checkbox |
| **Escape** | Close dropdown/modal | When dropdown/modal open |
| **Arrow Keys** | Navigate pagination/tabs | When focused on pagination/tab |
| **Ctrl+F** | Find text on page | Browser feature |

#### Tab Shortcut Use

**Windows/Linux**: Ctrl+Tab
**Mac**: Cmd+Option+Right Arrow

Navigate between browser tabs while testing keyboard accessibility.

---

### 4. Screen Reader Support

Dashboard announces all important information to screen readers:

#### Screen Readers Tested

- **NVDA** (Windows, free) - Full support
- **JAWS** (Windows, commercial) - Full support
- **VoiceOver** (Mac/iOS, built-in) - Full support
- **TalkBack** (Android, built-in) - Full support

#### What's Announced

**Metric Cards**:
```
Screen reader says: "2,400 total instances"
Shows: Card title, value, units
```

**Buttons**:
```
Screen reader says: "Refresh, button"
Shows: Button label, button type
```

**Table Headers**:
```
Screen reader says: "Process name, column header"
Shows: Column purpose
```

**Status Messages**:
```
Screen reader says: "SLA all met, status message"
Shows: Current state
```

#### Using Screen Reader

**Activate Screen Reader**:
- **Windows + NVDA**: Download from nvaccess.org, install, Run NVDA
- **Windows + JAWS**: Use Insert+H for help
- **Mac + VoiceOver**: Cmd+F5 to toggle
- **Mobile**: Activated in Accessibility settings

**Common Commands**:
- **H**: Read heading
- **T**: Go to table
- **G**: Go to graphics
- **L**: Go to list
- **Up/Down Arrows**: Navigate within element
- **Enter**: Activate link or button

---

### 5. Color Contrast

All text meets WCAG AA contrast ratio (≥4.5:1 for normal text):

#### Text Contrast Examples

| Element | Contrast | Status |
|---------|----------|--------|
| Black text on white | 21:1 | ✓ PASS (exceeds 4.5:1) |
| Dark blue on white | 8:1 | ✓ PASS |
| Chart colors | ≥3:1 | ✓ PASS (UI component standard) |
| Focus ring | 8:1+ | ✓ PASS (stands out clearly) |

#### Verify Contrast

Use WebAIM Contrast Checker:
1. Visit webaim.org/resources/contrastchecker/
2. Enter foreground (text) color
3. Enter background (element) color
4. Check ratio ≥4.5:1

---

### 6. Semantic HTML

Dashboard uses proper HTML tags for meaning:

#### Semantic Tags Used

| Tag | Purpose | Effect |
|-----|---------|--------|
| `<article>` | Main content section | Screen readers understand section importance |
| `<main>` | Dashboard main content | Screen readers skip to main content |
| `<h1>`, `<h2>` | Headings | Document structure for navigation |
| `<label>` | Form labels | Properly linked input fields |
| `<button>` | Buttons | Announced as "button" not just text |
| `<table>` | Data tables | Screen readers understand rows/columns |

#### Impact for Users

- Screen readers can navigate by heading (press H)
- Screen readers can navigate by table (press T)
- Proper form labeling for input fields
- Clear document structure

---

### 7. ARIA Labels & Descriptions

Dashboard adds ARIA (Accessible Rich Internet Applications) labels:

#### ARIA Examples

**Metric Card**:
```
<article aria-label="2,400 total instances">
  2,400
</article>
Announced by screen reader: "2,400 total instances"
```

**Button with icon**:
```
<button aria-label="Refresh dashboard">
  <RefreshIcon aria-hidden="true" />
</button>
Announced: "Refresh dashboard, button"
Icon ignored (aria-hidden=true)
```

**Status region**:
```
<div role="status" aria-label="All SLAs on track">
  ✓ All SLAs met
</div>
Announced as status: "All SLAs on track"
```

#### Common ARIA Roles

| Role | Purpose | Use Case |
|------|---------|----------|
| `role="region"` | Important section | "Execution Time Trends" section |
| `role="status"` | Status message | "SLA all met" announcement |
| `role="main"` | Main content | Main dashboard container |
| `role="progressbar"` | Progress indicator | SLA progress bar (aria-valuenow=75) |
| `role="button"` | Clickable element | Custom button-like elements |
| `role="link"` | Navigable element | Custom link-like elements |

---

## Using Accessibility Features

### Scenario 1: Blind User with Screen Reader

**Goal**: Review today's incident count and recent failed processes

**Steps**:
1. **Launch screen reader** (NVDA, JAWS, VoiceOver, or TalkBack)
2. **Navigate to dashboard** (screen reader reads page title)
3. **Press H** to jump to first heading
4. **Navigate headings** (H key) to find:
   - "Execution Summary"
   - "Incidents"
5. **Screen reader announces**:
   - "Incidents: 15 incidents"
6. **Tab to Incidents tab**, press Enter
7. **Press T** to jump to incident table
8. **Screen reader reads** each incident:
   - "Row 1: INV-001, InvoiceProcessing, FAILED, DB timeout"
   - "Row 2: ORD-042, OrderProcessing, FAILED, 404 error"

**Result**: User gets complete understanding of incidents without seeing screen

---

### Scenario 2: Motor Impairment - Keyboard Only

**Goal**: Filter processes to failed status and set 24h period

**Steps**:
1. **Only use keyboard** (no mouse)
2. **Press Tab** to focus on period selector
3. **Tab until 24h button focused** (blue ring visible)
4. **Press Enter** to select 24h
5. **Tab to Status filter**
6. **Tab to "Failed" checkbox**
7. **Press Space** to check Failed status
8. **Tab to Apply button**
9. **Press Enter** to apply filter
10. **Tab through results** with Tab key
11. **For details**: Press Enter on focused result

**Result**: Complete dashboard control without mouse (Tab for everything)

---

### Scenario 3: Low Vision - Magnification

**Goal**: Find incident details with screen magnification

**Steps**:
1. **Enable screen magnification** (Windows: Magnifier, Mac: Zoom, or browser zoom)
2. **Zoom to 200%** (Ctrl+Plus key or browser zoom)
3. **Navigate dashboard** with mouse or touch
4. **Use keyboard Tab** to navigate when can't see where to click
5. **Focus ring** helps locate current position
6. **Click Incidents tab** to see incident list
7. **Click incident** to view details (or Tab+Enter)

**Result**: All text readable at 200% zoom, can still interact with all controls

---

### Scenario 4: Hearing Impaired

**Goal**: Understand all status messages and indicators

**Current**: All visual, no audio
- Metric cards display colors (green, red, orange)
- Focus ring clearly visible
- Status messages visible (not audio)
- Error messages in text
- Icons supplemented with text labels

**Result**: Fully accessible, no audio required

---

## Testing Accessibility

### Quick Accessibility Check

Use this checklist:

- [ ] **Can navigate entire dashboard with Tab key only?**
  - Tab through all buttons, filters, tables
  - All elements reachable?
  - Focus ring visible everywhere?

- [ ] **Can activate all functions with keyboard?**
  - Enter key works on buttons
  - Space key works on checkboxes
  - Escape closes modals

- [ ] **Does screen reader announce all content?**
  - Use NVDA on Windows or VoiceOver on Mac
  - Metric values announced
  - Button labels clear
  - Table structure understandable

- [ ] **Color contrast sufficient?**
  - Text readable on backgrounds
  - Use WebAIM Contrast Checker
  - Ratio ≥4.5:1

- [ ] **Can zoom to 200% without issues?**
  - Ctrl++ in browser multiple times
  - No text cut off
  - Can still interact

- [ ] **No flashing content?**
  - Nothing flashes more than 3 times/second
  - No seizure risk

---

### Browser DevTools Accessibility Check

**Chrome DevTools**:
1. Open DevTools (F12)
2. Go to Lighthouse tab
3. Select "Accessibility" only
4. Click "Analyze page load"
5. View accessibility score (target ≥95)
6. Review violations listed

**Firefox Accessibility Inspector**:
1. Open DevTools (F12)
2. Go to Inspector → Accessibility tab
3. Explore element tree
4. Check "Accessibility Checker" panel
5. Review any issues flagged

---

### Axe DevTools Extension

**Install**: Browser extensions "Axe DevTools" (Chrome/Firefox/Edge)

**Use**:
1. Open dashboard
2. Click Axe DevTools icon
3. Click "Scan ALL of my page"
4. View results (target: 0 violations)
5. Review any Critical/Serious issues
6. Minor issues can be noted for future

**Target Score**: 0 violations, 0 errors

---

## Accessibility Resources

### Built-in OS Accessibility

**Windows**:
- Narrator (built-in screen reader) - Win+Ctrl+Enter
- Magnifier (built-in zoom) - Win+Plus
- High contrast mode - Right-Alt+Left-Shift+Print Screen

**Mac**:
- VoiceOver (screen reader) - Cmd+F5
- Zoom (magnification) - System Preferences → Accessibility
- Invert colors - System Preferences → Accessibility

**Mobile**:
- **iPhone**: VoiceOver in Settings → Accessibility
- **Android**: TalkBack in Settings → Accessibility
- **Windows 11 Phone**: Narrator in Settings → Accessibility

### Testing Tools

- **NVDA Screen Reader**: nvaccess.org (Windows, free)
- **JAWS Screen Reader**: freedomscientific.com (Windows, commercial)
- **WebAIM Contrast Checker**: webaim.org/resources/contrastchecker/
- **Axe DevTools**: deque.com/axe/devtools/ (Chrome/Firefox/Edge)
- **Lighthouse**: Built into Chrome DevTools
- **Accessibility Insights**: accessibilityinsights.io (Microsoft tool)

### Learning Resources

- **W3C WCAG 2.1 Standard**: w3.org/WAI/WCAG21/quickref/
- **ARIA Authoring Practices**: w3.org/WAI/ARIA/apg/
- **Inclusive Components**: inclusive-components.design/
- **WebAIM Articles**: webaim.org/articles/

---

## WCAG 2.1 Level AA Compliance

Easy BPM Dashboard meets WCAG 2.1 Level AA in:

### Principle 1: Perceivable

- ✅ **1.3 Adaptable**: Information presented in multiple ways (color, text, icons)
- ✅ **1.4 Distinguishable**: Color contrast ≥4.5:1, resizable text, not color-only

### Principle 2: Operable

- ✅ **2.1 Keyboard Accessible**: All functions keyboard accessible
- ✅ **2.4 Navigable**: Clear focus indicators, meaningful link labels, proper heading structure

### Principle 3: Understandable

- ✅ **3.1 Readable**: Clear language, semantic HTML, proper heading hierarchy
- ✅ **3.2 Predictable**: Consistent navigation, no unexpected changes
- ✅ **3.3 Input Assistance**: Error messages clear, labels associated with inputs

### Principle 4: Robust

- ✅ **4.1 Compatible**: Valid HTML, proper ARIA labels, compatible with assistive tech

---

## Accessibility Best Practices for Users

### For Daily Use

1. **Use keyboard shortcuts**: Tab, Enter, Escape keys faster than mouse
2. **Use screen reader**: Hear information while doing other tasks
3. **Zoom as needed**: 200% zoom often clearer than 100%
4. **Use color filters**: High contrast mode easier on eyes
5. **Use shortcuts**: Ctrl+F for find, Ctrl+A to select

### For Reports & Sharing

1. **Save filtered views**: Bookmark URL with filters applied
2. **Screenshot for documentation**: Shows current state
3. **Export data**: Use API endpoints to get raw data
4. **Share findings**: Export metrics, attach to reports
5. **Document steps**: Record keyboard navigation steps for reproducibility

---

## Accessibility Checklist for Operations Teams

Before using dashboard in production:

- [ ] Tested keyboard navigation (Tab through entire flow)
- [ ] Confirmed focus indicators visible
- [ ] Ran screen reader test (NVDA, JAWS, VoiceOver)
- [ ] Verified color contrast (≥4.5:1)
- [ ] Tested zoom to 200% (all readable)
- [ ] Ran Axe DevTools scan (0 violations)
- [ ] Lighthouse accessibility score ≥95
- [ ] Tested on multiple devices (mobile, tablet, desktop)
- [ ] Staff trained on keyboard shortcuts
- [ ] Support team informed of accessibility features

---

## Common Accessibility Questions

### Q: I can't see the focus ring on buttons. How do I know where I am?

**A**: Try these:
1. Check browser zoom (try Ctrl+Plus)
2. Enable high contrast mode (Win+Alt+Left-Shift+Print Screen)
3. Adjust focus ring color in browser settings
4. Use screen reader (announces focused element)

---

### Q: Can I use the dashboard without a keyboard?

**A**: Yes, all features available with mouse/touch. Keyboard just makes it faster.

---

### Q: Does the dashboard work with screen readers?

**A**: Yes, tested with NVDA, JAWS, VoiceOver, TalkBack. Full support.

---

### Q: Can I zoom in to see better?

**A**: Yes, use Ctrl++ (Windows/Linux) or Cmd++ (Mac) to zoom up to 200%.

---

### Q: Is there audio in the dashboard?

**A**: No, all information conveyed visually or through screen readers. Works for deaf/blind users.

---

### Q: What accessibility features are best for dyslexia?

**A**: 
1. Use screen reader to hear text read aloud
2. Increase font size (zoom to 150-200%)
3. Use high contrast mode
4. Take breaks to avoid eye strain

---

## Getting Help

### Accessibility Issues

- **Found violation?** Contact support with:
  - Browser name and version
  - Exact steps to reproduce
  - What expected vs. what happened
  - Screenshot if possible

### Accessibility Features

- **Need different format?** Ask for:
  - Large print version
  - Data export format
  - Different color scheme
  - Additional keyboard shortcut

### Assistive Technology

- **Using assistive tech?** Tell us:
  - Screen reader name (NVDA/JAWS/VoiceOver)
  - OS (Windows/Mac/iOS/Android)
  - Any issues encountered
  - What would help

---

## Accessibility Statement

The Easy BPM Admin Dashboard is designed to be accessible to users with disabilities in accordance with **WCAG 2.1 Level AA**.

**If you experience accessibility barriers**:
1. Contact support team
2. Describe issue in detail
3. We'll work to resolve quickly

**Commitment**: Easy BPM is committed to digital accessibility and continuously improving based on user feedback.

---

## Next Steps

- Learn [keyboard navigation shortcuts](./easy-admin-dashboard-accessibility.md#keyboard-shortcuts)
- Enable screen reader on your device
- Test dashboard with assistive technology
- Provide accessibility feedback to support team
- Review [troubleshooting guide](./easy-admin-dashboard-troubleshooting.md)

---

**Accessibility is not an afterthought — it's essential for inclusive design.** 🎯

If you have accessibility questions or need assistance, please reach out to support!
