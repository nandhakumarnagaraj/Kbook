# Canvas Design — Data Visualization for KhanaBook

## Overview

Standards for Chart.js charts, data visualizations, and interactive graphics used in the KhanaBook web admin dashboard.

---

## Chart.js Integration

### Installation & Import

```bash
npm install chart.js ng2-charts
```

One-time registration in your component or module (required before any chart renders):

```typescript
import { Chart, registerables } from 'chart.js';
Chart.register(...registerables);
```

This must be done once per application entrypoint — typically in `main.ts` or the component that uses charts.

### Chart Types in Use

| Chart Type | Where Used | Data |
|------------|-----------|------|
| Doughnut | Dashboard | Payment method distribution, order status breakdown |
| Bar | Dashboard | Weekly revenue trend, daily order counts |
| Line | Dashboard | Revenue over time, order trends |

---

## Doughnut Chart Pattern (Dashboard)

```typescript
readonly paymentChartData = computed<ChartData<'doughnut'>>(() => ({
  labels: ['Online', 'Cash', 'UPI', 'Pending'],
  datasets: [{
    data: [onlineTotal, cashTotal, upiTotal, pendingTotal],
    backgroundColor: [
      getComputedStyle(document.documentElement).getPropertyValue('--brand').trim() || '#C85A00',
      getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#16A34A',
      getComputedStyle(document.documentElement).getPropertyValue('--info').trim() || '#0284C7',
      getComputedStyle(document.documentElement).getPropertyValue('--warn').trim() || '#D97706',
    ],
    borderWidth: 0,
    borderRadius: 4,
    spacing: 4,
  }]
}));

readonly doughnutOptions: ChartOptions<'doughnut'> = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '72%',
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: 'var(--panel)',
      titleColor: 'var(--ink)',
      bodyColor: 'var(--ink-secondary)',
      borderColor: 'var(--line)',
      borderWidth: 1,
      padding: 12,
      cornerRadius: 8,
    }
  }
};
```

### Center Label Technique

```html
<div class="doughnut-wrapper" [style.--brand]="brandColor">
  <canvas baseChart
    [data]="chartData()"
    [options]="doughnutOptions"
    type="doughnut">
  </canvas>
  <div class="doughnut-center">
    <span class="center-value">{{ totalRevenue | currency }}</span>
    <span class="center-label">Total Revenue</span>
  </div>
</div>
```

```css
.doughnut-wrapper {
  position: relative;
  width: 200px;
  height: 200px;
}
.doughnut-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.center-value {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--ink);
}
.center-label {
  font-size: 0.7rem;
  color: var(--muted);
}
```

---

## Bar Chart Pattern (Weekly Trend)

```typescript
readonly barChartData = computed<ChartData<'bar'>>(() => ({
  labels: weekLabels,
  datasets: [{
    label: 'Revenue',
    data: weekRevenue,
    backgroundColor: brandColor,
    hoverBackgroundColor: brandLightColor,
    borderRadius: 6,
    borderSkipped: false,
  }]
}));

readonly barOptions: ChartOptions<'bar'> = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: 'var(--panel)',
      titleColor: 'var(--ink)',
      bodyColor: 'var(--ink-secondary)',
      borderColor: 'var(--line)',
      borderWidth: 1,
      padding: 12,
      cornerRadius: 8,
      callbacks: {
        label: (ctx) => `₹${ctx.parsed.y.toLocaleString()}`
      }
    }
  },
  scales: {
    x: {
      display: true,
      grid: { display: false },
      ticks: { font: { size: 11, weight: 'bold' }, color: '#64748b' }
    },
    y: {
      display: true,
      grid: { color: 'rgba(100, 116, 139, 0.1)' },
      ticks: { font: { size: 10 }, color: '#64748b' },
      beginAtZero: true
    }
  }
};
```

---

## Theme-Aware Charts

Charts must respond to theme changes. Use `effect()` to update colors when theme toggles:

```typescript
private readonly themeService = inject(ThemeService);
private chartRef?: Chart;

constructor() {
  effect(() => {
    // Force reactive update when theme changes
    this.themeService.isDarkMode();
    this.updateChartColors();
  });
}

private getCSSVar(name: string): string {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(name).trim() || fallback;
}
```

---

## Animation Standards

```typescript
readonly chartDefaults = {
  animation: {
    duration: 800,
    easing: 'easeOutQuart' as const,
  },
  transitions: {
    active: {
      animation: { duration: 300 }
    }
  }
};
```

---

## Responsive Chart Sizing

```css
.chart-container {
  width: 100%;
  min-height: 280px;
  position: relative;
}

@media (max-width: 768px) {
  .chart-container {
    min-height: 220px;
  }
}

@media (max-width: 480px) {
  .chart-container {
    min-height: 180px;
  }
}
```

---

## Data Visualization Best Practices

1. **Always use theme-aware colors** — read CSS variables at render time
2. **Show data labels only on hover** — keeps charts clean
3. **Doughnut cutout at 72%** — provides space for center label
4. **Bar border radius: 6px** — consistent with UI radius scale
5. **No dataset borders** — `borderWidth: 0` or minimal
6. **Currency formatting** — use Intl.NumberFormat or custom pipe
7. **Empty state** — show "No data available" centered message
8. **Loading state** — show shimmer placeholder matching chart dimensions
9. **Error state** — show retry option for failed data fetches
10. **Tooltip styling** — must match panel styling (background, border radius, shadow)

---

## Accessibility for Charts

- Provide `aria-label` describing chart data in text
- Include a data table fallback below the chart for screen readers
- Use sufficient color contrast for chart segments
- Avoid color-only differentiation — use patterns or labels alongside colors
- Respect `prefers-reduced-motion` by disabling chart animations
