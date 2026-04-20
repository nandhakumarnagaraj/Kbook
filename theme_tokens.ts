/* ── KhanaBook Design Tokens ── */

export const lightTheme = {
  primary:      '#3D1F00', // Espresso Brown
  secondary:    '#F5A623', // Saffron Gold
  accent:       '#6B3FA0', // Royal Violet
  bg:           '#FFFDF8',
  surface:      '#FFF6E8',
  surface2:     '#F2EBE0',
  textPrimary:  '#2B1500',
  textMuted:    '#7A5C3A',
  border:       '#E5D5C0',
  success:      '#2D7A4F',
  danger:       '#C0392B',
  successBg:    '#2D7A4F1A',
  dangerBg:     '#C0392B1A',
};

export const darkTheme = {
  primary:      '#F5A623', // Gold dominant on dark
  secondary:    '#9B72D0', // Softened violet
  accent:       '#FFD07A', // Pale gold
  bg:           '#1A0D00', // Deep espresso
  surface:      '#2A1500',
  surface2:     '#3D2310',
  textPrimary:  '#FFF0D6',
  textMuted:    '#C49A5E',
  border:       '#5C3A1E',
  success:      '#4CAF7D',
  danger:       '#E85C4A',
  successBg:    '#4CAF7D1A',
  dangerBg:     '#E85C4A1A',
};

/* Typography */
export const typography = {
  fontFamily: 'Poppins, Inter, sans-serif',
  xs:    11,
  sm:    13,
  base:  15,
  md:    17,
  lg:    22,
  xl:    28,
};

/* Spacing (in px/dp) */
export const spacing = {
  xs: 4, 
  sm: 8, 
  md: 12, 
  lg: 16, 
  xl: 24, 
  '2xl': 32
};

/* Border radius (in px/dp) */
export const radius = {
  sm: 6, 
  md: 10, 
  lg: 14, 
  xl: 20
};

/* Component Rules Summary */
export const componentRules = {
  primaryButton: {
    bg: '#F5A623',
    textLight: '#2B1500',
    textDark: '#1A0D00',
    radius: 10
  },
  topNav: {
    lightBg: '#3D1F00',
    darkBg: '#2A1500'
  },
  bottomNav: {
    lightBg: '#FFF6E8',
    darkBg: '#2A1500'
  }
};
