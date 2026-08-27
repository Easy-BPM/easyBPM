import React from 'react';

export const EasyBpmLogoMark: React.FC<{ className?: string; alt?: string }> = ({
  className = 'h-10 w-10',
  alt = 'Easy BPM'
}) => (
  <img
    src="/easy-bpm-logo.png"
    alt={alt}
    className={`${className} rounded-md object-cover shadow-lg shadow-blue-600/25`}
  />
);

