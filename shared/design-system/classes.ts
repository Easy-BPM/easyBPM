type StatusChipValue = 'COMPLETED' | 'FAILED' | 'TIMEOUT';

export const controlShellClass = 'control-shell';
export const controlPanelClass = 'control-panel';
export const controlPanelMutedClass = 'control-panel-muted';
export const controlPanelHeaderClass = 'control-panel-header';
export const controlInputClass = 'control-input';
export const controlButtonClass = 'control-button';
export const controlButtonPrimaryClass = 'control-button control-button-primary';
export const controlButtonDangerClass = 'control-button control-button-danger';
export const controlChipClass = 'control-chip';
export const controlChipAccentClass = 'control-chip control-chip-accent';
export const controlChipSuccessClass = 'control-chip control-chip-success';
export const controlChipWarningClass = 'control-chip control-chip-warning';
export const controlChipDangerClass = 'control-chip control-chip-danger';
export const controlEmptyStateClass = 'control-empty-state';

export const statusChipClass = (status: StatusChipValue | string) => {
  switch (status) {
    case 'COMPLETED':
      return controlChipSuccessClass;
    case 'FAILED':
      return controlChipDangerClass;
    case 'TIMEOUT':
      return controlChipWarningClass;
    default:
      return controlChipClass;
  }
};
