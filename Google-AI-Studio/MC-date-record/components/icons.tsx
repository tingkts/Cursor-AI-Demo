import React from 'react';

export const TrashIcon: React.FC<{ className?: string }> = ({ className = "w-5 h-5" }) => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className={className}>
    <path fillRule="evenodd" d="M8.75 1A2.75 2.75 0 0 0 6 3.75V4.5h8V3.75A2.75 2.75 0 0 0 11.25 1h-2.5ZM10 4c.84 0 1.5.66 1.5 1.5v1.5h3.375C15.507 7 16 7.492 16 8.125s-.493 1.125-1.125 1.125H3.625A1.125 1.125 0 0 1 2.5 8.125S3 7.618 3 7c0-.618.493-1.125 1.125-1.125H7.5V5.5C7.5 4.66 8.16 4 9 4h1ZM3.5 9.25v6.25A2.25 2.25 0 0 0 5.75 18h8.5A2.25 2.25 0 0 0 16.5 15.5V9.25h-13Z" clipRule="evenodd" />
  </svg>
);

export const RestoreIcon: React.FC<{ className?: string }> = ({ className = "w-5 h-5" }) => (
  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className={className}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 15 3 9m0 0 6-6M3 9h12a6 6 0 0 1 0 12h-3" />
  </svg>
);
