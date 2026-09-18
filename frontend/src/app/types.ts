
export type ShortenResponse = {
  shortURL: string;
  shortCode: string;
  originalURL: string;
  createdAt: string;
  expiresAt: string | null;
}

export type URLStats = {
  shortCode: string;
  originalURL: string;
  clickCount: number;
  createdAt: string;
  expiresAt: string | null;
  active: boolean;
  createdBy: string;
}
