import React from 'react';
import { DateEntry } from '../types';
import { TrashIcon } from './icons';

interface DateListProps {
  dateEntries: DateEntry[];
  onDeleteDate: (id: string) => void;
}

const formatDateForDisplay = (date: Date): string => {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const calculateDaysBetween = (date1: Date, date2: Date): number => {
  const timeDiff = date1.getTime() - date2.getTime();
  return Math.round(timeDiff / (1000 * 60 * 60 * 24));
};

const DateList: React.FC<DateListProps> = ({ dateEntries, onDeleteDate }) => {
  if (dateEntries.length === 0) {
    return <p className="text-center text-slate-500 py-4">目前沒有任何日期記錄。</p>;
  }

  // Group entries by year, storing original index for global calculations
  const groupedByYear = dateEntries.reduce<Record<number, { entry: DateEntry, originalIndex: number }[]>>((acc, entry, index) => {
    const year = entry.dateObject.getUTCFullYear();
    if (!acc[year]) {
      acc[year] = [];
    }
    acc[year].push({ entry, originalIndex: index });
    return acc;
  }, {});

  // Sort years in descending order (newest first)
  const sortedYears = Object.keys(groupedByYear)
                          .map(Number)
                          .sort((a, b) => b - a);

  return (
    <div className="space-y-8">
      {sortedYears.map(year => (
        <div key={year} className="bg-white shadow-xl rounded-lg border border-slate-200 overflow-hidden">
          <h3 className="text-xl font-semibold text-slate-700 py-4 px-4 sm:px-6 bg-slate-50 border-b border-slate-200">{year}年</h3>
          <div className="overflow-x-auto">
            <table className="min-w-full">
              <thead className="bg-slate-100">
                <tr>
                  <th className="py-3 px-4 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">編號</th>
                  <th className="py-3 px-4 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">日期</th>
                  <th className="py-3 px-4 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">與前筆間隔 (天)</th>
                  <th className="py-3 px-4 text-center text-xs font-semibold text-slate-600 uppercase tracking-wider">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {groupedByYear[year].map(({ entry, originalIndex }) => {
                  let intervalText = 'N/A';
                  if (originalIndex > 0) {
                    const prevEntry = dateEntries[originalIndex - 1];
                    const days = calculateDaysBetween(entry.dateObject, prevEntry.dateObject);
                    intervalText = `${days} 天`;
                  }
                  const displayDate = formatDateForDisplay(entry.dateObject);
                  return (
                    <tr key={entry.id} className="hover:bg-slate-50 transition-colors duration-150">
                      <td className="py-3 px-4 text-sm text-slate-600">{originalIndex + 1}</td>
                      <td className="py-3 px-4 text-sm text-slate-700 font-medium">{displayDate}</td>
                      <td className="py-3 px-4 text-sm text-slate-600">{intervalText}</td>
                      <td className="py-3 px-4 text-center">
                        <button
                          onClick={() => onDeleteDate(entry.id)}
                          className="text-red-500 hover:text-red-700 p-1 rounded-full hover:bg-red-100 transition-colors duration-150"
                          aria-label={`刪除日期 ${displayDate}`}
                        >
                          <TrashIcon className="w-5 h-5" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
};

export default DateList;
