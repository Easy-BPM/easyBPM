import React from 'react';
import logoUrl from '../assets/easy-bpm-logo.png';

export const EasyBpmLogoMark: React.FC<{ className?: string; alt?: string }> = ({
  className = 'h-10 w-10',
  alt = 'Easy BPM'
}) => (
  <img
    src={logoUrl}
    alt={alt}
    className={`${className} rounded-md object-cover shadow-lg shadow-blue-600/25`}
  />
);
