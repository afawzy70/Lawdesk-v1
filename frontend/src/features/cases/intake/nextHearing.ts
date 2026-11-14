export interface NextHearingValue {
  date: string;
  time: string;
  notes: string;
}

export const NO_NEXT_HEARING: NextHearingValue = { date: '', time: '', notes: '' };
